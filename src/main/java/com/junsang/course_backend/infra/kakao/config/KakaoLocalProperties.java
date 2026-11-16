package com.junsang.course_backend.infra.kakao.config;

import java.net.URI;
import java.util.List;
import com.junsang.course_backend.domain.place.collection.budget.ApiCredential;
import com.junsang.course_backend.domain.place.collection.budget.ApiScope;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(URI baseUrl, List<ApiCredential> credentials) {
    public KakaoLocalProperties {
        credentials = credentials == null ? List.of() : List.copyOf(credentials);
        var ids = new java.util.HashSet<String>();
        for (var credential : credentials) {
            if (credential.id() == null || credential.id().isBlank() || !ids.add(credential.id()))
                throw new IllegalArgumentException("API 인증 정보 id는 비어 있거나 중복될 수 없습니다.");
        }
    }

    public boolean isApiKeyConfigured() {
        return credentials.stream().anyMatch(c -> c.configured(ApiScope.KAKAO_KEYWORD));
    }
}
