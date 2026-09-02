package com.junsang.course_backend.domain.place.collection.pipeline.naver.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NaverSearchUrlCreatorTest {

    private final NaverSearchUrlCreator creator = new NaverSearchUrlCreator();

    @Test
    void appendsLocalityOnlyWhenItIsMissingFromPlaceName() {
        assertThat(creator.create("포도앤커피", "서울 마포구 합정동 394-15"))
                .isEqualTo("https://map.naver.com/p/search/%ED%8F%AC%EB%8F%84%EC%95%A4%EC%BB%A4%ED%94%BC%20%ED%95%A9%EC%A0%95");
        assertThat(creator.create("노란코끼리 합정점", "서울 마포구 합정동 393-25"))
                .isEqualTo("https://map.naver.com/p/search/%EB%85%B8%EB%9E%80%EC%BD%94%EB%81%BC%EB%A6%AC%20%ED%95%A9%EC%A0%95%EC%A0%90");
    }
}
