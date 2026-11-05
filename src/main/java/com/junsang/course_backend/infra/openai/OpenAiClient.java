package com.junsang.course_backend.infra.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import com.junsang.course_backend.infra.openai.config.AiProviderProperties;
import java.util.Map;
import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/// OpenAI Responses API에 구조화된 JSON 생성을 요청한다.
@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiClient {

    private static final int MAX_OUTPUT_TOKENS = 500;

    private final AiProviderProperties properties;
    private final RestClient.Builder restClientBuilder;

    // JSON Schema를 강제한 Responses API 결과에서 출력 텍스트만 반환한다.
    public String createStructuredResponse(
            String instructions,
            String input,
            Map<String, Object> schema
    ) {
        // 키가 없는 환경에서 외부 호출을 시도하지 않고 명확한 설정 오류로 종료한다.
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.OPENAI_API_KEY_NOT_CONFIGURED);
        }

        // 모델이 정한 JSON Schema만 반환하도록 요청 본문을 구성한다.
        Map<String, Object> request = Map.of(
                "model", properties.selected().model(),
                "store", false,
                "max_output_tokens", MAX_OUTPUT_TOKENS,
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
            // 저장하지 않는 단발성 Responses 요청으로 장소 묶음을 태깅한다.
            JsonNode response = client().post()
                    .uri("/v1/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new BusinessException(ErrorCode.OPENAI_API_EMPTY_RESPONSE);
            }

            // Responses API의 output 배열에서 JSON Schema 결과 텍스트를 추출한다.
            return outputText(response);

        } catch (RestClientResponseException exception) {
            log.warn("OpenAI Responses API failed: status={}", exception.getStatusCode());
            throw new BusinessException(ErrorCode.OPENAI_API_REQUEST_FAILED, exception);
        } catch (RestClientException exception) {
            log.warn("OpenAI Responses API request failed: {}", exception.getMessage());
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

    // Responses 요청도 Batch와 같은 조직·프로젝트 컨텍스트를 사용한다.
    private void applyAuthentication(HttpHeaders headers) {
        headers.setBearerAuth(properties.selected().apiKey());
        if (StringUtils.hasText(properties.selected().organization())) {
            headers.set("OpenAI-Organization", properties.selected().organization());
        }
        if (StringUtils.hasText(properties.selected().project())) {
            headers.set("OpenAI-Project", properties.selected().project());
        }
    }
}
