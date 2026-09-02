package com.junsang.course_backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통 ──────────────────────────────────────────────────────────────
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // ── 추천 ──────────────────────────────────────────────────────────────
    ACTIVITY_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "활동 카테고리를 찾을 수 없습니다."),
    COURSE_ANCHOR_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 지역의 코스 중심지를 찾을 수 없습니다."),
    PREFERENCE_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "선호도 옵션을 찾을 수 없습니다."),

    // ── 관리자 ────────────────────────────────────────────────────────────
    LOCAL_ADMIN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "로컬 환경에서만 접근할 수 있습니다."),

    // ── 외부 API ──────────────────────────────────────────────────────────
    KAKAO_API_KEY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "카카오 API 키가 설정되지 않았습니다."),
    KAKAO_LOCAL_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "카카오 장소 검색 호출에 실패했습니다."),
    KAKAO_LOCAL_API_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "카카오 장소 검색 응답을 받지 못했습니다."),
    NAVER_SEARCH_API_KEY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "네이버 검색 API 키가 설정되지 않았습니다."),
    NAVER_LOCAL_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "네이버 지역 검색 호출에 실패했습니다."),
    NAVER_BLOG_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "네이버 블로그 검색 호출에 실패했습니다."),
    NAVER_LOCAL_API_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "네이버 지역 검색 응답을 받지 못했습니다."),
    NAVER_BLOG_API_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "네이버 블로그 검색 응답을 받지 못했습니다."),
    OPENAI_API_KEY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI API 키가 설정되지 않았습니다."),
    OPENAI_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "OpenAI API 호출에 실패했습니다."),
    OPENAI_API_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "OpenAI API 응답을 받지 못했습니다.");

    private final HttpStatus status;
    private final String message;
}
