package com.junsang.course_backend.domain.place.collection.budget;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestClientResponseException;

class CollectionBudgetServiceTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-11-16T06:00:00Z"), ZoneId.of("Asia/Seoul"));
    final CollectionBudgetWriter writer = mock(CollectionBudgetWriter.class);
    final Map<String, CollectionBudgetWriter.Block> persisted = new HashMap<>();
    final List<ApiCredential> keys = List.of(new ApiCredential("main", "id-a", "secret-a"), new ApiCredential("team", "id-b", "secret-b"));
    CollectionBudgetService service;
    @BeforeEach void setup() {
        when(writer.find(any(), anyString())).thenAnswer(i -> Optional.ofNullable(persisted.get(i.getArgument(0)+":"+i.getArgument(1))));
        doAnswer(i -> {
            persisted.put(i.getArgument(0)+":"+i.getArgument(1), new CollectionBudgetWriter.Block(i.getArgument(2), i.getArgument(3)));
            return null;
        }).when(writer).block(any(), anyString(), any(), anyString());
        service = new CollectionBudgetService(writer, clock, new ObjectMapper());
    }
    @Test void kakaoQuotaSwitchesKeyAndRestartRemembersBlockWithoutBlockingCategory() {
        AtomicInteger calls = new AtomicInteger();
        assertThat(service.<String>execute(ApiScope.KAKAO_KEYWORD, keys, key -> {
            calls.incrementAndGet();
            if (key.id().equals("main")) throw response(400, "{\"code\":-10,\"msg\":\"API limit has been exceeded.\"}");
            return "ok";
        })).isEqualTo("ok");
        assertThat(calls).hasValue(2);
        var restarted = new CollectionBudgetService(writer, clock, new ObjectMapper());
        assertThat(restarted.execute(ApiScope.KAKAO_KEYWORD, keys, ApiCredential::id)).isEqualTo("team");
        assertThat(restarted.execute(ApiScope.KAKAO_CATEGORY, keys, ApiCredential::id)).isEqualTo("main");
        assertThat(persisted.get("KAKAO_KEYWORD:main").until()).isEqualTo(Instant.parse("2026-11-16T15:00:00Z"));
    }
    @Test void allNaverKeysExhaustedWaitUntilNextMonthAndShareSearchScope() {
        assertThatThrownBy(() -> service.execute(ApiScope.NAVER_SEARCH, keys, key -> {
            throw response(429, "{\"error\":{\"errorCode\":\"400\",\"message\":\"Quota Exceeded\"}}");
        })).isInstanceOfSatisfying(CollectionDeferredException.class, e ->
                assertThat(e.retryAt()).isEqualTo(Instant.parse("2026-11-30T15:00:00Z")));
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> service.execute(ApiScope.NAVER_SEARCH, keys, key -> calls.incrementAndGet()))
                .isInstanceOf(CollectionDeferredException.class);
        assertThat(calls).hasValue(0);
    }
    @Test void dailyNaverQuotaWaitsForKoreanMidnightEvenWhenClockUsesUtc() {
        var utcService = new CollectionBudgetService(writer, clock.withZone(ZoneOffset.UTC), new ObjectMapper());
        assertThatThrownBy(() -> utcService.execute(ApiScope.NAVER_SEARCH, keys, key -> {
            throw response(429, "{\"error\":{\"errorCode\":429,\"message\":\"일별 사용량 한도가 초과하였습니다.\"}}");
        })).isInstanceOfSatisfying(CollectionDeferredException.class, e ->
                assertThat(e.retryAt()).isEqualTo(Instant.parse("2026-11-16T15:00:00Z")));
    }
    @Test void unknown429IsShortCooldownNotMonthlyQuota() {
        assertThatThrownBy(() -> service.execute(ApiScope.NAVER_SEARCH, keys.subList(0,1), key -> {
            throw response(429, "{\"error\":{\"errorCode\":\"420\"}}");
        })).isInstanceOfSatisfying(CollectionDeferredException.class, e ->
                assertThat(e.retryAt()).isEqualTo(clock.instant().plusSeconds(60)));
    }
    @Test void authenticationBlocksKeyUntilOperatorFixAndDoesNotExposeSecret() {
        service.execute(ApiScope.NAVER_SEARCH, keys, key -> {
            if (key.id().equals("main")) throw response(401, "{}");
            return "ok";
        });
        assertThat(persisted.get("NAVER_SEARCH:main").until()).isNull();
        assertThat(keys.getFirst().toString()).doesNotContain("secret-a", "id-a");
    }
    @Test void externalServerFailureDoesNotExhaustCredential() {
        assertThatThrownBy(() -> service.execute(ApiScope.KAKAO_KEYWORD, keys, key -> { throw response(503, "{}"); }))
                .isInstanceOf(RestClientResponseException.class);
        verify(writer, never()).block(any(), anyString(), any(), anyString());
    }
    @Test void resetMakesPreviouslyExhaustedKeyUsableAgain() {
        persisted.put("KAKAO_KEYWORD:main", new CollectionBudgetWriter.Block(clock.instant().minusSeconds(1), "QUOTA"));
        assertThat(service.<String>execute(ApiScope.KAKAO_KEYWORD, keys, ApiCredential::id)).isEqualTo("main");
    }
    private RestClientResponseException response(int status, String body) {
        return new RestClientResponseException("remote failure", status, "error", HttpHeaders.EMPTY,
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
    }
}
