package com.junsang.course_backend.domain.place.collection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LegalDongAreaResolverTest {

    @Test
    void resolvesSeoulLegalDongFromKakaoAddress() {
        LegalDongAreaResolver resolver = new LegalDongAreaResolver();

        assertThat(resolver.resolveAreaCode("서울특별시 성동구 성수동1가 656-1"))
                .isEqualTo("SEOUL_SEONGSU");
        assertThat(resolver.resolveAreaCode("경기도 성남시 분당구"))
                .isNull();
    }
}
