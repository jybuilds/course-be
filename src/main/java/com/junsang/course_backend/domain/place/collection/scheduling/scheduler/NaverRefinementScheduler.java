package com.junsang.course_backend.domain.place.collection.scheduling.scheduler;

import com.junsang.course_backend.domain.place.collection.budget.CollectionDeferredException;
import com.junsang.course_backend.domain.place.collection.pipeline.naver.service.PlaceRefinementService;

import com.junsang.course_backend.domain.place.collection.scheduling.policy.CollectionPolicy;
import com.junsang.course_backend.domain.place.collection.scheduling.service.*;
import com.junsang.course_backend.infra.naver.config.NaverSearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("naverRefinementWorker")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="place-collection.scheduling.enabled", havingValue="true")
public class NaverRefinementScheduler {
    private final CollectionPolicy policy;
    private final PlaceRefinementService service;
    private final CollectionExecutionState state;
    private final NaverSearchProperties naverSearch;

    @Scheduled(fixedDelayString="#{@collectionPolicy.settings().naver.intervalMillis}", scheduler="naverCollectionScheduler")
    public void run() {
        try {
            execute();
        } catch (CollectionDeferredException waiting) {
            log.debug("수집 작업 대기");
        } catch (RuntimeException error) {
            log.warn("NaverRefinementScheduler 실행 실패: {}", error.getClass().getSimpleName());
        }
    }

    private void execute() {
        if (!naverSearch.isConfigured() || !state.canStartWork()) return;
        int capacity = state.naverCapacity();
        if (capacity > 0) service.refinePending(capacity);
    }
}
