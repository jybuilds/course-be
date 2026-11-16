package com.junsang.course_backend.domain.place.collection.pipeline.naver.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NaverPlaceNormalizerTest {

    private final NaverPlaceNormalizer normalizer = new NaverPlaceNormalizer();

    @Test
    void normalizesHtmlNameAndMetropolitanAddress() {
        assertThat(normalizer.cleanTitle("<b>성심당</b>  본점"))
                .isEqualTo("성심당 본점");
        assertThat(normalizer.normalizeName("<b>Room-Escape</b> 블랙점"))
                .isEqualTo("roomescape블랙점");
        assertThat(normalizer.normalizeAddress(" 서울특별시 중구  을지로15길 6-5 "))
                .isEqualTo("서울중구을지로15길6-5");
    }
}
