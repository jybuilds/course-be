package com.junsang.course_backend.domain.place.collection.scheduling.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCollectionJob;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionProfileRepository;

/** 초기 Area의 프로필 Job 전체를 한 트랜잭션에서 등록한다. */
@Service @RequiredArgsConstructor
public class CollectionJobRegistrationService {
    private final JdbcTemplate jdbc;
    private final AreaRepository areas;
    private final PlaceCollectionJobRepository jobs;
    private final PlaceCollectionProfileRepository profiles;

    @Transactional
    public boolean register(Long areaId) {
        jdbc.queryForObject("SELECT id FROM areas WHERE id=? FOR UPDATE", Long.class, areaId);
        if (!jobs.findByAreaIdAndParentJobIsNullOrderByIdAsc(areaId).isEmpty()) return false;
        var active = profiles.findByProviderAndIsActiveTrueOrderById(PlaceProvider.KAKAO);
        if (active.isEmpty()) throw new IllegalStateException("활성 카카오 수집 프로필이 없습니다.");
        var area = areas.findById(areaId).orElseThrow();
        String runKey = UUID.randomUUID().toString();
        jobs.saveAll(active.stream().map(profile -> PlaceCollectionJob.initial(area, profile, runKey)).toList());
        return true;
    }
}
