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
    public String submit(String jsonLines) { return submit(jsonLines, properties.provider()); }

    public String submit(String jsonLines, String provider) {
        requireConfigured(provider);
        RestClient client = client(provider);
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
            if ("OPENAI".equalsIgnoreCase(provider)) {
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
            log.warn("AI Batch HTTP failure: provider={}, stage={}, status={}", provider, stage, exception.getStatusCode());
            throw new BusinessException(ErrorCode.OPENAI_API_REQUEST_FAILED, exception);
        } catch (RestClientException | IllegalStateException exception) {
            if ("BATCH_CREATE".equals(stage)) {
                throw new SubmissionUnknownException(exception);
            }
            log.warn("AI Batch request failure: provider={}, stage={}, type={}", provider, stage, exception.getClass().getSimpleName());
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

    public record BatchPoll(String status, String output, JsonNode errors) {
        public boolean terminal() { return java.util.Set.of("completed", "failed", "expired", "cancelled").contains(status); }
    }

    public BatchPoll poll(String batchId, String provider) {
        requireConfigured(provider);
        RestClient client = client(provider);
        JsonNode batch = client.get().uri("/v1/batches/{batchId}", batchId).retrieve().body(JsonNode.class);
        String status = requiredText(batch, "status");
        BatchPoll state = new BatchPoll(status, "", batch.path("errors"));
        if (!state.terminal()) return state;
        java.util.List<String> outputs = new java.util.ArrayList<>();
        for (String field : java.util.List.of("output_file_id", "error_file_id")) {
            JsonNode file = batch.path(field);
            if (file.isTextual() && !file.asText().isBlank()) {
                String text = client.get().uri("/v1/files/{id}/content", file.asText()).retrieve().body(String.class);
                if (text != null && !text.isBlank()) outputs.add(text.strip());
            }
        }
        return new BatchPoll(status, String.join("\n", outputs), batch.path("errors"));
    }

    // 기존 단건 호출자와 테스트 호환용. 파이프라인은 종료 상태의 부분 결과도 받는 poll을 사용한다.
    public String completedOutput(String batchId) {
        BatchPoll result = poll(batchId, properties.provider());
        if (!result.terminal()) return null;
        if (!"completed".equals(result.status())) throw new IllegalStateException("원격 Batch 종료: " + result.status());
        if (result.output().isBlank()) throw new IllegalStateException("완료된 Batch 결과 파일 없음");
        return result.output();
    }

    private RestClient client(String provider) {
        var settings = properties.selected(provider);
        RestClient.Builder builder = restClientBuilder.clone();
        // 외부 호출은 수집 lease보다 짧게 제한해 무한 대기를 막는다.
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10)).version(HttpClient.Version.HTTP_1_1).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(60));
        builder.requestFactory(factory);
        return builder.baseUrl(settings.baseUrl().toString()).defaultHeaders(headers -> {
            headers.setBearerAuth(settings.apiKey());
            if (StringUtils.hasText(settings.organization())) headers.set("OpenAI-Organization", settings.organization());
            if (StringUtils.hasText(settings.project())) headers.set("OpenAI-Project", settings.project());
        }).build();
    }

    private void requireConfigured(String provider) {
        var active = properties.selected(provider);
        if (active == null || active.baseUrl() == null || !StringUtils.hasText(active.apiKey()) || !StringUtils.hasText(active.model()))
            throw new BusinessException(ErrorCode.OPENAI_API_KEY_NOT_CONFIGURED);
    }

    private String requiredText(JsonNode node, String field) {
        String value = node == null || !node.path(field).isTextual() ? "" : node.path(field).asText();
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.OPENAI_API_EMPTY_RESPONSE);
        }
        return value;
    }
}
