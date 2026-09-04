package com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.junsang.course_backend.infra.naver.dto.response.NaverBlogSearchResponse.NaverBlogItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class NaverBlogEvidenceFilterTest {

    @Test
    void keepsSearchResultsStripsHtmlAndLimitsToTen() {
        List<NaverBlogItem> items = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> new NaverBlogItem(
                        "<b>성심당</b> 본점 후기 " + index,
                        "대전 <b>성심당</b> 방문 후기",
                        "20260831"
                ))
                .toList();
        items = new java.util.ArrayList<>(items);
        items.add(new NaverBlogItem("", "", "20260831"));

        String evidence = new NaverBlogEvidenceFilter().filter(items);

        assertThat(evidence).doesNotContain("<b>");
        assertThat(evidence.split("\\n")).hasSize(10);
        assertThat(evidence).startsWith("1. [20260831] 제목: 성심당 본점 후기 0 | 내용: 대전 성심당 방문 후기");
    }
}
