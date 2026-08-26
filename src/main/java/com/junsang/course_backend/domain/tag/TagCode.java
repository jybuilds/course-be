package com.junsang.course_backend.domain.tag;

import java.util.List;

/// 유사한 사용자 선택지를 하나로 합쳐 추천 점수에 사용하는 대표 키워드다.
public enum TagCode {
    EXHIBITION("전시", "전시 관람", "갤러리", "미술관·박물관"),
    POPUP("팝업", "팝업스토어", "브랜드 체험"),
    SHOPPING("쇼핑", "편집숍", "빈티지 쇼핑"),
    MARKET("시장 구경", "플리마켓"),
    WALK("산책", "골목 구경"),
    PARK("공원", "피크닉"),
    RIVERSIDE("한강", "강변 산책"),
    MOVIE("영화", "독립영화"),
    PERFORMANCE("공연", "뮤지컬·연극"),
    BOOKSTORE("서점", "독립서점"),
    WORKSHOP("공방", "원데이 클래스"),
    GAME("게임", "오락실·보드게임"),
    CAFE("카페", "커피"),
    BREAD("빵", "베이커리 카페"),
    DESSERT("디저트", "달콤한 디저트"),
    KOREAN_FOOD("한식"),
    WESTERN_FOOD("양식"),
    ASIAN_FOOD("아시안 음식"),
    QUIET("조용한", "여유로운"),
    LIVELY("활기찬", "사람 많은"),
    ROMANTIC("데이트", "로맨틱한"),
    TRENDY("힙한", "요즘 뜨는"),
    COZY("아늑한"),
    INDOOR("실내"),
    OUTDOOR("야외"),
    SCENIC_VIEW("뷰 좋은", "야경");

    private final String displayName;
    private final List<String> options;

    TagCode(String... options) {
        this.displayName = options[0];
        this.options = List.of(options);
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getOptions() {
        return options;
    }
}
