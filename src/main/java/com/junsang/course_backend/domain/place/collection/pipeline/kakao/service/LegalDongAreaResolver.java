package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/// 카카오 지번 주소의 법정동을 서비스 Area 코드로 변환한다.
@Component
public class LegalDongAreaResolver {
    private final List<Entry> entries;

    // 서울 법정동 CSV를 한 번 읽고, 긴 법정동명부터 비교할 수 있게 정렬한다.
    public LegalDongAreaResolver() {
        try (var stream = getClass().getClassLoader().getResourceAsStream("db/seed/city/seoul/seoul-area-legal-dong-mappings.csv")) {
            if (stream == null) throw new IllegalStateException("서울 법정동 매핑 CSV를 찾을 수 없습니다.");
            entries = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().skip(1)
                    .map(line -> line.replace("\"", "").split(","))
                    .map(row -> new Entry(row[1], row[2]))
                    .sorted(Comparator.comparingInt((Entry entry) -> entry.fullName().length()).reversed()).toList();
        } catch (Exception exception) { throw new IllegalStateException("서울 법정동 매핑을 읽을 수 없습니다.", exception); }
    }

    // 주소 전체에 포함되는 가장 긴 법정동명을 사용해 축약 주소 오매칭을 줄인다.
    public String resolveAreaCode(String addressName) {
        if (addressName == null) {
            return null;
        }

        String normalizedAddressName = addressName.replace("서울특별시", "서울");
        return entries.stream()
                .filter(entry -> normalizedAddressName.contains(entry.kakaoAddressName()))
                .map(Entry::areaCode)
                .findFirst()
                .orElse(null);
    }

    private record Entry(String fullName, String areaCode) {
        // 카카오 주소의 '서울' 표기와 법정동 원본의 '서울특별시' 표기를 맞춘다.
        private String kakaoAddressName() {
            return fullName.replace("서울특별시", "서울");
        }
    }
}
