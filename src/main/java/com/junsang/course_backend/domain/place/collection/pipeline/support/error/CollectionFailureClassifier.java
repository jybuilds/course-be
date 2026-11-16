package com.junsang.course_backend.domain.place.collection.pipeline.support.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.global.exception.BusinessException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** 공급자 응답의 명시적인 코드와 필드만 사용한다. 헤더의 cooldown을 quota reset으로 추정하지 않는다. */
public final class CollectionFailureClassifier {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static CollectionFailure classify(String provider, Throwable error, PlaceRefinementErrorCode fallback) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException response) {
                JsonNode body = null;
                try { body = JSON.readTree(response.getResponseBodyAsString()); } catch (Exception ignored) { }
                return http(provider, response.getStatusCode().value(), body, response.getResponseHeaders());
            }
            if (cause instanceof ResourceAccessException) return failure(provider, "TEMPORARY_FAILURE");
            if (cause instanceof DataAccessException) return new CollectionFailure(
                    PlaceRefinementErrorCode.valueOf(provider + "_STORAGE_FAILED"),
                    "DB 저장 실패: " + cause.getClass().getSimpleName());
        }
        if (error instanceof BusinessException business) {
            String code = business.getErrorCode().name();
            if (code.endsWith("KEY_NOT_CONFIGURED") || code.equals("NAVER_SEARCH_API_KEY_NOT_CONFIGURED"))
                return new CollectionFailure(PlaceRefinementErrorCode.valueOf(code), "API 설정 누락");
            if (code.endsWith("EMPTY_RESPONSE"))
                return new CollectionFailure(PlaceRefinementErrorCode.valueOf(code), "외부 API 응답 없음");
            try { return new CollectionFailure(PlaceRefinementErrorCode.valueOf(code), "외부 API 오류: " + code); }
            catch (IllegalArgumentException unknown) { /* 알려지지 않은 공통 오류만 단계별 fallback을 사용한다. */ }
        }
        return new CollectionFailure(fallback, "처리 실패: " + error.getClass().getSimpleName());
    }

    public static CollectionFailure http(String provider, int status, JsonNode body, HttpHeaders headers) {
        JsonNode error = body == null ? null : body.has("error") ? body.get("error") : body;
        String type = field(error, "type") + " " + field(error, "code");
        String message = field(error, "message").toLowerCase(Locale.ROOT);
        if ("AI".equals(provider) && (type.contains("content_policy") || type.contains("content_filter")
                || message.contains("flagged as potentially violating")))
            return new CollectionFailure(PlaceRefinementErrorCode.AI_RESPONSE_REFUSED, "AI 요청/응답 정책 거절");
        boolean quota = type.contains("usage_limit_reached") || type.contains("insufficient_quota")
                || type.contains("quota_exceeded") || type.contains("credits_exhausted")
                || message.contains("usage limit has been reached") || message.contains("quota exceeded");
        String suffix = quota ? "QUOTA_EXHAUSTED"
                : status == 401 || status == 403 ? "AUTH_FAILED"
                : status == 429 ? "RATE_LIMITED"
                : status == 408 || status >= 500 ? "TEMPORARY_FAILURE" : "INVALID_REQUEST";
        LocalDateTime hint = quota ? resetAt(error) : retryAfter(headers);
        return new CollectionFailure(PlaceRefinementErrorCode.valueOf(provider + "_API_" + suffix),
                provider + " HTTP " + status + " / " + suffix, hint);
    }

    private static LocalDateTime resetAt(JsonNode error) {
        if (error == null || !error.isObject()) return null;
        JsonNode absolute = error.get("resets_at");
        JsonNode seconds = error.get("resets_in_seconds");
        try {
            Instant reset = absolute != null && absolute.canConvertToLong()
                    ? Instant.ofEpochSecond(absolute.longValue())
                    : seconds != null && seconds.canConvertToLong() && seconds.longValue() >= 0
                    ? Instant.now().plusSeconds(seconds.longValue()) : null;
            return reset == null ? null : LocalDateTime.ofInstant(reset, ZoneId.systemDefault());
        } catch (DateTimeException | ArithmeticException ignored) { return null; }
    }

    private static LocalDateTime retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst("Retry-After");
        if (value == null) return null;
        try { return LocalDateTime.now().plusSeconds(Math.max(0, Long.parseLong(value))); }
        catch (NumberFormatException ignored) {
            try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(); }
            catch (DateTimeException invalid) { return null; }
        }
    }

    private static String field(JsonNode node, String name) { return node == null ? "" : node.path(name).asText(""); }
    private static CollectionFailure failure(String provider, String suffix) {
        return new CollectionFailure(PlaceRefinementErrorCode.valueOf(provider + "_API_" + suffix), provider + " " + suffix);
    }
    private CollectionFailureClassifier() { }
}
