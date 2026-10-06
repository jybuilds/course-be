package com.junsang.course_backend.recommendation.dto.response;

import java.util.List;

/// 사용자 선택에 따라 추천된 Area 목록이다.
public record RecommendationAreaResponse(
        List<AreaResponse> areas
) {

    /// 추천 Area 한 건의 기본 정보다.
    public record AreaResponse(
            Long areaId,
            String name,
            String tier,
            double score
    ) {
    }
}
