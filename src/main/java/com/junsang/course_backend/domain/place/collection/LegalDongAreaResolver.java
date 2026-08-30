package com.junsang.course_backend.domain.place.collection;

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
        if (addressName == null) return null;
        return entries.stream().filter(entry -> addressName.contains(entry.fullName())).map(Entry::areaCode).findFirst().orElse(null);
    }

    private record Entry(String fullName, String areaCode) { }
}
