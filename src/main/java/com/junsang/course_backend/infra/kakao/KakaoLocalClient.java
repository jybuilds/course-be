package com.junsang.course_backend.infra.kakao;

import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import com.junsang.course_backend.infra.kakao.config.KakaoLocalProperties;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoKeywordSearchRequest;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoCategorySearchRequest;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoKeywordSearchResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoLocalClient {

    private final KakaoLocalProperties properties;
    private final RestClient restClient;

    public KakaoLocalClient(RestClient.Builder restClientBuilder, KakaoLocalProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
    }

    // 수집 프로필의 키워드와 rect 범위로 카카오 장소 검색을 요청한다.
    public KakaoKeywordSearchResponse searchKeyword(KakaoKeywordSearchRequest request) {
        if (!properties.isApiKeyConfigured()) {
            throw new BusinessException(ErrorCode.KAKAO_API_KEY_NOT_CONFIGURED);
        }

        KakaoKeywordSearchResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", request.query())
                            .queryParam("rect", request.rect())
                            .queryParam("size", request.size())
                            .queryParam("page", request.page())
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                    .retrieve()
                    .body(KakaoKeywordSearchResponse.class);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.KAKAO_LOCAL_API_REQUEST_FAILED);
        }

        if (response == null) {
            throw new BusinessException(ErrorCode.KAKAO_LOCAL_API_EMPTY_RESPONSE);
        }
        return response;
    }

    // 수집 프로필의 카테고리와 rect 범위로 카카오 장소 검색을 요청한다.
    public KakaoKeywordSearchResponse searchCategory(KakaoCategorySearchRequest request) {
        if (!properties.isApiKeyConfigured()) throw new BusinessException(ErrorCode.KAKAO_API_KEY_NOT_CONFIGURED);
        try {
            KakaoKeywordSearchResponse response = restClient.get().uri(uriBuilder -> uriBuilder
                    .path("/v2/local/search/category.json").queryParam("category_group_code", request.categoryGroupCode())
                    .queryParam("rect", request.rect()).queryParam("size", request.size()).queryParam("page", request.page()).build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey()).retrieve().body(KakaoKeywordSearchResponse.class);
            if (response == null) throw new BusinessException(ErrorCode.KAKAO_LOCAL_API_EMPTY_RESPONSE);
            return response;
        } catch (RestClientException exception) { throw new BusinessException(ErrorCode.KAKAO_LOCAL_API_REQUEST_FAILED); }
    }
}
