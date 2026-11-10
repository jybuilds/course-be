package com.junsang.course_backend.infra.openai.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import com.junsang.course_backend.infra.openai.config.AiProviderProperties;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/// OpenAI 파일 업로드와 비동기 Batch API 호출을 담당한다.
@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiBatchClient {

    private static final int FILE_PROCESSING_ATTEMPTS = 20;
    private static final long FILE_PROCESSING_INTERVAL_MILLIS = 500;

    private final AiProviderProperties properties;
    private final RestClient.Builder restClientBuilder;

    // JSONL 요청 파일을 업로드한 뒤 OpenAI Batch Job을 제출한다.
    public String submit(String jsonLines) {
        requireConfigured();
        RestClient client = client();
        String stage = "FILE_UPLOAD";
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

            // OmniRoute 파일 메타데이터에는 status가 없으며 Batch 생성 시 파일을 검증한다.
            stage = "FILE_PROCESSING";
            if ("OPENAI".equalsIgnoreCase(properties.provider())) {
                awaitFileProcessing(client, fileId);
            }

            stage = "BATCH_CREATE";

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
            try {
                return requiredText(batch, "id");
            } catch (BusinessException exception) {
                throw new SubmissionUnknownException(exception);
            }
        } catch (RestClientResponseException exception) {
            if ("BATCH_CREATE".equals(stage)
                    && (exception.getStatusCode().is5xxServerError() || exception.getStatusCode().value() == 408)) {
                throw new SubmissionUnknownException(exception);
            }
            log.warn("AI Batch HTTP failure: provider={}, stage={}, status={}, response={}",
                    properties.provider(), stage, exception.getStatusCode(),
                    exception.getResponseBodyAsString().replace(properties.selected().apiKey(), "[REDACTED]"));
            throw new BusinessException(ErrorCode.OPENAI_API_REQUEST_FAILED, exception);
        } catch (RestClientException | IllegalStateException exception) {
            if ("BATCH_CREATE".equals(stage)) {
                throw new SubmissionUnknownException(exception);
            }
            log.warn("AI Batch request failure: provider={}, stage={}", properties.provider(), stage, exception);
            throw new BusinessException(ErrorCode.OPENAI_API_REQUEST_FAILED, exception);
        }
    }

    public static class SubmissionUnknownException extends RuntimeException {
        public SubmissionUnknownException(Throwable cause) {
            super("원격 AI 배치 접수 여부를 확인할 수 없습니다.", cause);
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
            // 실패 줄은 별도 error 파일에 있으므로 성공·실패 결과를 함께 수집한다.
            java.util.List<String> outputs = new java.util.ArrayList<>();
            for (String field : java.util.List.of("output_file_id", "error_file_id")) {
                JsonNode fileId = batch.path(field);
                if (fileId.isTextual() && !fileId.asText().isBlank()) {
                    String content = client().get()
                            .uri("/v1/files/{fileId}/content", fileId.asText())
                            .retrieve()
                            .body(String.class);
                    if (content != null && !content.isBlank()) {
                        outputs.add(content.strip());
                    }
                }
            }
            if (outputs.isEmpty()) {
                throw new IllegalStateException("완료된 AI Batch에 성공·실패 결과 파일이 없습니다: " + batchId);
            }
            return String.join("\n", outputs);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.OPENAI_API_REQUEST_FAILED, exception);
        }
    }

    private RestClient client() {
        RestClient.Builder builder = restClientBuilder.clone();
        if ("OMNIROUTE".equalsIgnoreCase(properties.provider())) {
            // 로컬 Node 게이트웨이의 h2c 업그레이드 연결 종료를 피한다.
            builder.requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()));
        }
        return builder
                .baseUrl(properties.selected().baseUrl().toString())
                .defaultHeaders(this::applyAuthentication)
                .build();
    }

    // 파일과 Batch 요청이 반드시 같은 조직·프로젝트 컨텍스트에서 실행되도록 공통 헤더를 적용한다.
    private void applyAuthentication(HttpHeaders headers) {
        headers.setBearerAuth(properties.selected().apiKey());
        if (StringUtils.hasText(properties.selected().organization())) {
            headers.set("OpenAI-Organization", properties.selected().organization());
        }
        if (StringUtils.hasText(properties.selected().project())) {
            headers.set("OpenAI-Project", properties.selected().project());
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.OPENAI_API_KEY_NOT_CONFIGURED);
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node == null || !node.path(field).isTextual() ? "" : node.path(field).asText();
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.OPENAI_API_EMPTY_RESPONSE);
        }
        return value;
    }
}
