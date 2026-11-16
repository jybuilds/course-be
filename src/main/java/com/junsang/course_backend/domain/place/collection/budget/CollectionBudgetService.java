package com.junsang.course_backend.domain.place.collection.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/** 실제 소진 응답으로만 키를 보류한다. HTTP 429만으로 월간 소진을 추측하지 않는다. */
@Service
public class CollectionBudgetService {
    private final CollectionBudgetWriter writer;
    private final Clock clock;
    private final ObjectMapper json;

    public CollectionBudgetService(CollectionBudgetWriter writer, @Qualifier("collectionClock") Clock clock, ObjectMapper json) {
        this.writer = writer;
        this.clock = clock;
        this.json = json;
    }

    public <T> T execute(ApiScope scope, List<ApiCredential> credentials, Function<ApiCredential, T> request) {
        Instant earliest = null;
        Set<String> ids = new HashSet<>();
        for (ApiCredential credential : credentials) {
            if (!credential.configured(scope)) continue;
            if (!ids.add(credential.id())) throw new IllegalArgumentException("중복 API 인증 식별자");
            var block = writer.find(scope, credential.id()).filter(value -> value.active(clock.instant()));
            if (block.isPresent()) {
                earliest = earlier(earliest, block.get().until());
                continue;
            }
            try {
                return request.apply(credential);
            } catch (RestClientResponseException error) {
                JsonNode body;
                try { body = json.readTree(error.getResponseBodyAsString()); }
                catch (Exception invalid) { body = json.createObjectNode(); }
                JsonNode detail = body != null && body.has("error") ? body.path("error") : body;
                String code = detail == null ? "" : detail.path(scope == ApiScope.NAVER_SEARCH ? "errorCode" : "code").asText("");
                // NCP 공통 gateway는 errorCode=400, Kakao는 code=-10으로 쿼터 소진을 알린다.
                boolean quota = scope == ApiScope.NAVER_SEARCH
                        ? error.getStatusCode().value() == 429 && "400".equals(code)
                        : "-10".equals(code) || "-11".equals(code);
                int status = error.getStatusCode().value();
                String reason;
                Instant until;
                if (quota) {
                    reason = "QUOTA";
                    until = nextReset(scope);
                } else if (status == 401 || status == 403) {
                    reason = "AUTH";
                    until = null;
                } else if (status == 429) {
                    reason = "RATE_LIMIT";
                    until = retryAfter(error);
                } else {
                    throw error;
                }
                writer.block(scope, credential.id(), until, reason);
                earliest = earlier(earliest, until);
                // 월간 Naver 쿼터는 지역/블로그 공통 scope. 카카오 검색 종류는 서로 독립이다.
            }
        }
        throw new CollectionDeferredException(earliest == null ? clock.instant().plusSeconds(86400) : earliest);
    }

    private Instant nextReset(ApiScope scope) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        return scope == ApiScope.NAVER_SEARCH
                ? now.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(clock.getZone()).toInstant()
                : now.toLocalDate().plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    }

    private Instant retryAfter(RestClientResponseException error) {
        String header = error.getResponseHeaders() == null ? null : error.getResponseHeaders().getFirst("Retry-After");
        try { return clock.instant().plusSeconds(Math.max(1, Long.parseLong(header))); }
        catch (RuntimeException invalid) {
            try {
                Instant value = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return value.isAfter(clock.instant()) ? value : clock.instant().plusSeconds(60);
            } catch (RuntimeException ignored) { return clock.instant().plusSeconds(60); }
        }
    }

    private Instant earlier(Instant left, Instant right) {
        return right == null ? left : left == null || right.isBefore(left) ? right : left;
    }
}
