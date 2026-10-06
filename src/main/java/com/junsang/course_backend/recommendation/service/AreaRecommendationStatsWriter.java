package com.junsang.course_backend.recommendation.service;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.stats.entity.AreaStats;
import com.junsang.course_backend.domain.stats.repository.AreaStatsRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// Area 추천 목록의 노출 수를 기록한다.
@Component
@RequiredArgsConstructor
public class AreaRecommendationStatsWriter {

    private final AreaStatsRepository areaStatsRepository;
    private final EntityManager entityManager;

    // 추천 결과 전체의 통계를 한 번에 조회해 노출 수를 기록한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordImpressions(List<Area> areas) {
        Set<Long> areaIds = areas.stream().map(Area::getId).collect(Collectors.toSet());
        Map<Long, AreaStats> statsByAreaId = areaStatsRepository.findByAreaIdIn(areaIds).stream()
                .collect(Collectors.toMap(stats -> stats.getArea().getId(), Function.identity()));
        List<AreaStats> newStats = new ArrayList<>();

        for (Area area : areas) {
            AreaStats stats = statsByAreaId.get(area.getId());
            if (stats == null) {
                stats = AreaStats.create(entityManager.getReference(Area.class, area.getId()));
                newStats.add(stats);
            }
            stats.recordImpression();
        }

        areaStatsRepository.saveAll(newStats);
    }
}
