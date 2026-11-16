package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.*;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.support.error.*;
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

    @Transactional
    public PlaceCollectionJob tryStart(Long id) {
        PlaceCollectionJob job = find(id);
        if (job.getStatus() != CollectionJobStatus.READY && !(job.getStatus() == CollectionJobStatus.FAILED
                && job.getNextRetryAt() != null && !job.getNextRetryAt().isAfter(LocalDateTime.now()))) return null;
        if (job.getNextRetryAt() != null && job.getNextRetryAt().isAfter(LocalDateTime.now())) return null;
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

    @Transactional
    public void defer(Long id, LocalDateTime retryAt) {
        find(id).defer(retryAt);
    }

    private PlaceCollectionJob find(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("수집 Job 없음: " + id));
    }
}
