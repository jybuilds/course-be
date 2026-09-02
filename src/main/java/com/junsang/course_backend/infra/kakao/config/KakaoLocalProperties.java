package com.junsang.course_backend.infra.kakao.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(
        URI baseUrl,
        String restApiKey
) {

    public boolean isApiKeyConfigured() {
        // 키가 비어 있으면 외부 API를 호출하지 않고 설정 오류로 처리한다.
        return restApiKey != null && !restApiKey.isBlank();
    }
}
