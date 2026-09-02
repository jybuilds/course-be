package com.junsang.course_backend.infra.openai.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import com.junsang.course_backend.infra.openai.config.OpenAiProperties;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/// OpenAI 파일 업로드와 비동기 Batch API 호출을 담당한다.
@Component
@RequiredArgsConstructor
public class OpenAiBatchClient {

    private static final int FILE_PROCESSING_ATTEMPTS = 20;
    private static final long FILE_PROCESSING_INTERVAL_MILLIS = 500;

    private final OpenAiProperties properties;
    private final RestClient.Builder restClientBuilder;

    // JSONL 요청 파일을 업로드한 뒤 OpenAI Batch Job을 제출한다.
    public String submit(String jsonLines) {
        requireConfigured();
        RestClient client = client();
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("purpose", "batch");
            body.part("file", new ByteArrayResource(jsonLines.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return "place-tagging.jsonl";
                }
            });
            JsonNode file = client.post()
                    .uri("/v1/files")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(JsonNode.class);
            String fileId = requiredText(file, "id");

            // Batch가 파일을 읽기 전에 OpenAI의 파일 처리가 끝났는지 확인한다.
            awaitFileProcessing(client, fileId);

            JsonNode batch = client.post()
                    .uri("/v1/batches")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "input_file_id", fileId,
                            "endpoint", "/v1/responses",
                            "completion_window", "24h"
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            return requiredText(batch, "id");
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.OPENAI_API_REQUEST_FAILED, exception);
        }
    }

    // 업로드 직후의 uploaded 상태에서는 Batch가 입력 파일을 읽지 못할 수 있어 processed까지 짧게 대기한다.
    private void awaitFileProcessing(RestClient client, String fileId) {
        for (int attempt = 0; attempt < FILE_PROCESSING_ATTEMPTS; attempt++) {
            JsonNode file = client.get()
                    .uri("/v1/files/{fileId}", fileId)
                    .retrieve()
                    .body(JsonNode.class);
            String status = requiredText(file, "status");
            if ("processed".equals(status)) {
                return;
            }
            if ("error".equals(status)) {
                throw new IllegalStateException("OpenAI Batch 입력 파일 처리에 실패했습니다: " + fileId);
            }
            waitForFileProcessing(fileId);
        }
        throw new IllegalStateException("OpenAI Batch 입력 파일 처리가 제한 시간 안에 완료되지 않았습니다: " + fileId);
    }

    // 파일 상태를 확인하는 짧은 대기 중 인터럽트되면 현재 제출을 중단한다.
    private void waitForFileProcessing(String fileId) {
        try {
            TimeUnit.MILLISECONDS.sleep(FILE_PROCESSING_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI Batch 입력 파일 처리 대기 중 인터럽트되었습니다: " + fileId, exception);
        }
    }

    // 완료된 OpenAI Batch의 결과 JSONL을 반환하고, 아직 처리 중이면 null을 반환한다.
    public String completedOutput(String batchId) {
        requireConfigured();
        try {
            JsonNode batch = client().get()
                    .uri("/v1/batches/{batchId}", batchId)
                    .retrieve()
                    .body(JsonNode.class);
            String status = batch.path("status").asText();
            if ("failed".equals(status) || "expired".equals(status) || "cancelled".equals(status)) {
                throw new IllegalStateException("OpenAI Batch가 완료되지 않았습니다: " + status);
            }
            if (!"completed".equals(status)) {
                return null;
            }
            String outputFileId = requiredText(batch, "output_file_id");
            return client().get()
                    .uri("/v1/files/{fileId}/content", outputFileId)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.OPENAI_API_REQUEST_FAILED, exception);
        }
    }

    private RestClient client() {
        return restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .defaultHeaders(this::applyAuthentication)
                .build();
    }

    // 파일과 Batch 요청이 반드시 같은 조직·프로젝트 컨텍스트에서 실행되도록 공통 헤더를 적용한다.
    private void applyAuthentication(HttpHeaders headers) {
        headers.setBearerAuth(properties.apiKey());
        if (StringUtils.hasText(properties.organization())) {
            headers.set("OpenAI-Organization", properties.organization());
        }
        if (StringUtils.hasText(properties.project())) {
            headers.set("OpenAI-Project", properties.project());
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.OPENAI_API_KEY_NOT_CONFIGURED);
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node == null ? "" : node.path(field).asText();
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.OPENAI_API_EMPTY_RESPONSE);
        }
        return value;
    }
}
