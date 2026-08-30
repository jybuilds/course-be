package com.junsang.course_backend.domain.place.collection.naver.service;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/// 장소명과 지번주소의 동네명으로 네이버 검색어와 지도 링크를 만든다.
@Component
public class NaverSearchUrlCreator {

    // 장소명에 지번주소의 동네명을 한 번만 보완한 네이버 검색어를 만든다.
    public String createQuery(String name, String address) {
        String locality = extractLocality(address);
        return locality == null || normalize(name).contains(normalize(locality))
                ? name
                : name + " " + locality;
    }

    // 같은 검색어를 URL 경로에 인코딩한 사용자용 네이버 지도 검색 링크를 만든다.
    public String create(String name, String address) {
        return UriComponentsBuilder.fromUriString("https://map.naver.com")
                .pathSegment("p", "search", createQuery(name, address))
                .encode()
                .toUriString();
    }

    // 지번주소에서 법정동 표기를 찾아 검색어에 사용할 동네명으로 바꾼다.
    private String extractLocality(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        for (String token : address.trim().split("\\s+")) {
            if (token.endsWith("동") && token.length() > 1) {
                return token.substring(0, token.length() - 1);
            }
        }
        return null;
    }

    // 공백과 특수문자를 제외한 문자열로 동네명 포함 여부를 비교한다.
    private String normalize(String value) {
        return value.replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
