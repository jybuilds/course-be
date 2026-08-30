package com.junsang.course_backend.infra.naver.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.search")
public record NaverSearchProperties(
        URI baseUrl,
        String clientId,
        String clientSecret
) {

    // 클라이언트 ID와 Secret이 모두 설정됐는지 확인한다.
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
