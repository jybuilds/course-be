package com.junsang.course_backend.domain.place.collection.scheduling.scheduler;

import java.util.List;

import com.junsang.course_backend.domain.place.collection.budget.CollectionDeferredException;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.CollectionJobStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.KakaoAreaCollectionService;

import com.junsang.course_backend.domain.place.collection.scheduling.policy.CollectionPolicy;
import com.junsang.course_backend.domain.place.collection.scheduling.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("kakaoCollectionWorker")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="place-collection.scheduling.enabled", havingValue="true")
public class KakaoCollectionScheduler {
    private final CollectionPolicy policy;
    private final KakaoAreaCollectionService service;
    private final PlaceCollectionJobRepository jobs;
    private final CollectionJobRegistrationService registration;
    private final CollectionExecutionState state;

    @Scheduled(fixedDelayString="#{@collectionPolicy.settings().kakao.intervalMillis}", scheduler="kakaoCollectionScheduler")
    public void run() {
        try {
            execute();
        } catch (CollectionDeferredException waiting) {
            log.debug("수집 작업 대기");
        } catch (RuntimeException error) {
            log.warn("KakaoCollectionScheduler 실행 실패: {}", error.getClass().getSimpleName());
        }
    }

    private void execute() {
        if (!state.canCollectKakao()) return;
        if (!jobs.existsByStatusIn(List.of(
                CollectionJobStatus.READY,
                CollectionJobStatus.RUNNING))) {
            for (var area : policy.nextAreas()) registration.register(area.getId());
        }
        service.resumePendingJobs(policy.settings().getKakao().getBatchSize(), state::canCollectKakao);
    }
}
