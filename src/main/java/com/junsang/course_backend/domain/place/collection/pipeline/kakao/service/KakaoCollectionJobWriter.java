package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.*;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.support.error.*;
import com.junsang.course_backend.domain.place.entity.Area;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoCollectionJobWriter {
    private final PlaceCollectionJobRepository repository;
    private final JdbcTemplate jdbc;

    // 실행 전에 전체 프로필 root를 남겨 종료 시 아직 생성하지 않은 작업이 사라지지 않게 한다.
    @Transactional
    public List<PlaceCollectionJob> initialize(List<Area> areas, List<PlaceCollectionProfile> profiles) {
        List<PlaceCollectionJob> roots = new ArrayList<>();
        for (Area area : areas) {
            jdbc.queryForObject("SELECT id FROM areas WHERE id = ? FOR UPDATE", Long.class, area.getId());
            List<PlaceCollectionJob> history = repository.findByAreaIdAndParentJobIsNullOrderByIdAsc(area.getId());
            String previousKey = history.isEmpty() ? null : history.getLast().getCollectionRunKey();
            Integer outstanding = jdbc.queryForObject("SELECT count(*) FROM place_collection_jobs WHERE area_id=? "
                    + "AND collection_run_key IS NOT DISTINCT FROM ? AND (status IN ('READY','RUNNING') OR (status='FAILED' AND next_retry_at IS NOT NULL))",
                    Integer.class, area.getId(), previousKey);
            boolean resume = outstanding != null && outstanding > 0;
            String runKey = resume ? previousKey : UUID.randomUUID().toString();
            List<PlaceCollectionJob> existing = resume ? history.stream()
                    .filter(job -> Objects.equals(job.getCollectionRunKey(), runKey)).sorted(Comparator.comparing(PlaceCollectionJob::getId).reversed()).toList() : List.of();
            for (PlaceCollectionProfile profile : profiles) {
                roots.add(existing.stream().filter(job -> job.getCollectionProfile().getId().equals(profile.getId()))
                        .findFirst().orElseGet(() -> repository.save(PlaceCollectionJob.initial(area, profile, runKey))));
            }
        }
        return roots;
    }

    @Transactional
    public PlaceCollectionJob tryStart(Long id) {
        PlaceCollectionJob job = find(id);
        if (job.getStatus() != CollectionJobStatus.READY && !(job.getStatus() == CollectionJobStatus.FAILED
                && job.getNextRetryAt() != null && !job.getNextRetryAt().isAfter(LocalDateTime.now()))) return null;
        job.start();
        return job;
    }

    @Transactional
    public List<PlaceCollectionJob> split(Long id, boolean binary) {
        PlaceCollectionJob parent = find(id);
        List<PlaceCollectionJob> existing = repository.findByParentJobIdOrderByIdAsc(id);
        List<PlaceCollectionJob> children = new ArrayList<>(existing);
        for (PlaceCollectionJob proposed : parent.split(binary)) {
            boolean known = existing.stream().anyMatch(child ->
                    child.getMinLatitude().compareTo(proposed.getMinLatitude()) == 0
                    && child.getMinLongitude().compareTo(proposed.getMinLongitude()) == 0
                    && child.getMaxLatitude().compareTo(proposed.getMaxLatitude()) == 0
                    && child.getMaxLongitude().compareTo(proposed.getMaxLongitude()) == 0);
            if (!known) children.add(repository.save(proposed));
        }
        return children;
    }

    @Transactional
    public void fail(Long id, CollectionFailure failure) {
        PlaceCollectionJob job = find(id);
        job.fail(failure.code(), failure.message());
        job.scheduleRetry(CollectionErrorPolicy.nextRetry(failure, job.getAttemptCount(), LocalDateTime.now()));
    }

    private PlaceCollectionJob find(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("수집 Job 없음: " + id));
    }
}
