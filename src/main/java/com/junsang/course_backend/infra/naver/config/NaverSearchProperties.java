package com.junsang.course_backend.infra.naver.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import com.junsang.course_backend.domain.place.collection.budget.ApiCredential;
import com.junsang.course_backend.domain.place.collection.budget.ApiScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.search")
public record NaverSearchProperties(URI baseUrl, List<ApiCredential> credentials) {
    private static final Logger log = LoggerFactory.getLogger(NaverSearchProperties.class);

    public NaverSearchProperties {
        var valid = new ArrayList<ApiCredential>();
        var ids = new java.util.HashSet<String>();
        for (var credential : credentials == null ? List.<ApiCredential>of() : credentials) {
            if (credential.id() == null || credential.id().isBlank() || !ids.add(credential.id()))
                throw new IllegalArgumentException("API 인증 정보 id는 비어 있거나 중복될 수 없습니다.");
            if (!credential.configured(ApiScope.NAVER_SEARCH)) {
                if (hasValue(credential.keyId()) || hasValue(credential.key())) {
                    log.warn("네이버 검색 인증 정보가 일부 누락되어 제외합니다. id={}", credential.id());
                }
                continue;
            }
            if (!validKey(credential.keyId()) || !validKey(credential.key())) {
                log.warn("네이버 검색 인증 정보에 유효하지 않은 문자가 있어 제외합니다. id={}", credential.id());
                continue;
            }
            valid.add(credential);
        }
        credentials = List.copyOf(valid);
        if (credentials.isEmpty()) {
            log.warn("사용 가능한 네이버 검색 인증 정보가 없어 예약 정제를 실행하지 않습니다.");
        }
    }

    public boolean isConfigured() {
        return !credentials.isEmpty();
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean validKey(String value) {
        return value.chars().allMatch(c -> c >= 0x21 && c <= 0x7e);
    }
}
