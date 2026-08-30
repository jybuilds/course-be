package com.junsang.course_backend.infra.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import com.junsang.course_backend.infra.openai.config.OpenAiProperties;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/// OpenAI Responses API에 구조화된 JSON 생성을 요청한다.
@Component
public class OpenAiClient {

    private final OpenAiProperties properties;
    private final RestClient restClient;

    public OpenAiClient(RestClient.Builder restClientBuilder, OpenAiProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
    }

    // JSON Schema를 강제한 Responses API 결과에서 출력 텍스트만 반환한다.
    public String createStructuredResponse(
            String instructions,
            String input,
            Map<String, Object> schema
    ) {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.OPENAI_API_KEY_NOT_CONFIGURED);
        }

        Map<String, Object> request = Map.of(
                "model", properties.model(),
                "store", false,
                "instructions", instructions,
                "input", input,
                "reasoning", Map.of("effort", "none"),
                "text", Map.of("format", Map.of(
                        "type", "json_schema",
                        "name", "place_tagging",
                        "strict", true,
                        "schema", schema
                ))
        );

        try {
            JsonNode response = restClient.post()
                    .uri("/v1/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new BusinessException(ErrorCode.OPENAI_API_EMPTY_RESPONSE);
            }
            return outputText(response);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.OPENAI_API_REQUEST_FAILED, exception);
        }
    }

    // 메시지 배열에서 첫 번째 output_text를 찾는다.
    private String outputText(JsonNode response) {
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    String text = content.path("text").asText();
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        throw new BusinessException(ErrorCode.OPENAI_API_EMPTY_RESPONSE);
    }
}
