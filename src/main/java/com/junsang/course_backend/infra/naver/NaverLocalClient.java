package com.junsang.course_backend.infra.naver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import com.junsang.course_backend.infra.naver.config.NaverSearchProperties;
import com.junsang.course_backend.infra.naver.dto.response.NaverLocalSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/// 네이버 지역 검색 API로 장소 후보를 조회한다.
@Component
@Slf4j
public class NaverLocalClient {

    private static final int MAX_DISPLAY = 5;
    private static final String API_KEY_ID_HEADER = "X-NCP-APIGW-API-KEY-ID";
    private static final String API_KEY_HEADER = "X-NCP-APIGW-API-KEY";

    private final NaverSearchProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NaverLocalClient(
            RestClient.Builder restClientBuilder,
            NaverSearchProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
        this.objectMapper = objectMapper;
    }

    // 장소명과 주소로 네이버 지역 검색 결과를 최대 5개 조회한다.
    public NaverLocalSearchResponse search(String query) {
        if (query == null || query.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.NAVER_SEARCH_API_KEY_NOT_CONFIGURED);
        }

        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/v1/local")
                            .queryParam("query", query)
                            .queryParam("display", MAX_DISPLAY)
                            .queryParam("start", 1)
                            .queryParam("sort", "random")
                            .queryParam("format", "json")
                            .build())
                    .header(API_KEY_ID_HEADER, properties.clientId())
                    .header(API_KEY_HEADER, properties.clientSecret())
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new BusinessException(ErrorCode.NAVER_LOCAL_API_EMPTY_RESPONSE);
            }
            return objectMapper.readValue(body, NaverLocalSearchResponse.class);
        } catch (RestClientException | JsonProcessingException exception) {
            log.warn("Naver API Hub local search failed: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.NAVER_LOCAL_API_REQUEST_FAILED, exception);
        }
    }
}
