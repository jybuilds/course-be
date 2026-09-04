package com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service;

import com.junsang.course_backend.infra.naver.dto.response.NaverBlogSearchResponse.NaverBlogItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/// 블로그 검색 결과에서 빈 값과 중복된 글만 제거한다.
@Component
public class NaverBlogEvidenceFilter {

    private static final int MAX_EVIDENCE_COUNT = 10;

    // 네이버의 장소명·동네명 검색 결과를 최대 10개로 정리해 AI 입력 문자열로 만든다.
    public String filter(List<NaverBlogItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        List<Evidence> evidences = items.stream()
                .map(this::clean)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        Evidence::normalizedText,
                        evidence -> evidence,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .limit(MAX_EVIDENCE_COUNT)
                .toList();
        return formatEvidence(evidences);
    }

    // 목록 번호와 필드명을 붙여 AI가 각 블로그 자료를 구분하게 한다.
    private String formatEvidence(List<Evidence> evidences) {
        return java.util.stream.IntStream.range(0, evidences.size())
                .mapToObj(index -> evidences.get(index).toPromptText(index + 1))
                .collect(Collectors.joining("\n"));
    }

    // HTML 강조 태그와 엔티티를 제거하고 비어 있는 결과는 제외한다.
    private Evidence clean(NaverBlogItem item) {
        String title = cleanText(item.title());
        String description = cleanText(item.description());
        if (title.isBlank() && description.isBlank()) {
            return null;
        }
        return new Evidence(title, description, cleanText(item.postdate()));
    }

    // 네이버 응답에 포함된 HTML 태그와 엔티티를 일반 텍스트로 바꾼다.
    private String cleanText(String value) {
        return value == null ? "" : HtmlUtils.htmlUnescape(value.replaceAll("<[^>]*>", "")).trim();
    }

    private record Evidence(String title, String description, String postdate) {

        private String normalizedText() {
            return (title + " " + description).replaceAll("[^가-힣a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        }

        private String toPromptText(int order) {
            return order + ". [" + postdate + "] 제목: " + title + " | 내용: " + description;
        }
    }
}
