package com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service;

/// AI 태깅 응답과 저장 검증이 공유하는 기준값이다.
public final class AiTaggingPolicy {

    public static final int MIN_TAG_COUNT = 1;
    public static final int MAX_TAG_COUNT = 10;
    public static final int MAX_TAG_WEIGHT = 100;

    // 단건 Responses API와 비동기 Batch API가 같은 태깅 기준을 사용한다.
    public static String instructions() {
        return """
                한국 장소의 관련 태그와 적합도를 평가한다. 제공된 활성 태그 코드만 사용한다.
                placeTypeFinalized가 true면 입력 placeType을 그대로 반환하고 변경하지 않는다.
                false면 typeEvidence.naverCategory만 근거로 ACTIVITY, MEAL, CAFE 중 하나를 반환한다.
                Type을 판단할 때 tagEvidence의 장소명, 카카오 카테고리, 블로그 자료는 사용하지 않는다.
                태그는 tagEvidence 전체를 근거로 선택한다. 블로그 자료는 보조 근거일 뿐이며 확실하지 않은 분위기 태그는 선택하지 않는다.
                tags는 %d개 이상 %d개 이하이며 가중치는 0부터 %d 사이 정수다.
                """.formatted(MIN_TAG_COUNT, MAX_TAG_COUNT, MAX_TAG_WEIGHT);
    }

    private AiTaggingPolicy() {
    }
}
