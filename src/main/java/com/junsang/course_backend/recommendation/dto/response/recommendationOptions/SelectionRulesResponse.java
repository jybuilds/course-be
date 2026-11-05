package com.junsang.course_backend.recommendation.dto.response;

/// 추천 입력 항목별 선택 정책이다.
public record SelectionRulesResponse(
        SelectionRuleResponse city,
        SelectionRuleResponse tags,
        SelectionRuleResponse timeSlot,
        SelectionRuleResponse companionType
) {
    public static SelectionRulesResponse defaults() {
        return new SelectionRulesResponse(
                new SelectionRuleResponse(false),
                new SelectionRuleResponse(true),
                new SelectionRuleResponse(false),
                new SelectionRuleResponse(false)
        );
    }
}
record SelectionRuleResponse(
        boolean allowMultiple
){
}

