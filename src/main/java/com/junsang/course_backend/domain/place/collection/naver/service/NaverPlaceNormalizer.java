package com.junsang.course_backend.domain.place.collection.naver.service;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/// 네이버 장소명과 주소를 동일 장소 비교가 가능한 문자열로 정규화한다.
@Component
public class NaverPlaceNormalizer {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern NON_NAME_CHARACTER = Pattern.compile("[^\\p{L}\\p{N}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern ADDRESS_NUMBER = Pattern.compile("^\\d+(?:-\\d+)?(?:번지)?$");

    // 네이버 강조 HTML을 제거해 저장 가능한 장소명을 만든다.
    public String cleanTitle(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String unescaped = HtmlUtils.htmlUnescape(value);
        String withoutHtml = HTML_TAG.matcher(unescaped).replaceAll("");
        return WHITESPACE.matcher(withoutHtml.trim()).replaceAll(" ");
    }

    // HTML과 구분 문자를 제거해 장소명 비교 문자열을 만든다.
    public String normalizeName(String value) {
        return NON_NAME_CHARACTER.matcher(cleanTitle(value))
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
    }

    // 광역 행정구역 이름과 공백을 통일해 주소 비교 문자열을 만든다.
    public String normalizeAddress(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = normalizeAdministrativeName(value);
        return WHITESPACE.matcher(normalized).replaceAll("");
    }

    // 건물명·층·호수를 제외한 도로명 또는 지번 기본 주소를 만든다.
    public String normalizeBaseAddress(String value) {
        String[] tokens = normalizeAdministrativeName(value).split(" ");
        for (int index = 0; index < tokens.length; index++) {
            if (ADDRESS_NUMBER.matcher(tokens[index]).matches()) {
                return String.join("", java.util.Arrays.copyOfRange(tokens, 0, index + 1));
            }
        }
        return String.join("", tokens);
    }

    // 두 주소에 모두 상세 정보가 있고 서로 다르면 같은 장소로 보지 않는다.
    public boolean hasConflictingAddressDetail(String left, String right) {
        String leftDetail = normalizeAddressDetail(left);
        String rightDetail = normalizeAddressDetail(right);
        return !leftDetail.isEmpty() && !rightDetail.isEmpty() && !leftDetail.equals(rightDetail);
    }

    // 광역 행정구역 표기와 연속 공백을 통일한다.
    private String normalizeAdministrativeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return WHITESPACE.matcher(value.trim()
                .replace("서울특별시", "서울")
                .replace("부산광역시", "부산")
                .replace("대전광역시", "대전")
                .replace("대구광역시", "대구")
                .replace("인천광역시", "인천")
                .replace("광주광역시", "광주")
                .replace("울산광역시", "울산")
                .replace("세종특별자치시", "세종"))
                .replaceAll(" ");
    }

    // 번지 또는 건물번호 뒤에 붙은 상세 주소만 분리한다.
    private String normalizeAddressDetail(String value) {
        String[] tokens = normalizeAdministrativeName(value).split(" ");
        for (int index = 0; index < tokens.length; index++) {
            if (ADDRESS_NUMBER.matcher(tokens[index]).matches()) {
                return String.join("", java.util.Arrays.copyOfRange(tokens, index + 1, tokens.length));
            }
        }
        return "";
    }
}
