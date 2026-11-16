package com.junsang.course_backend.domain.place.collection.scheduling.service;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import com.junsang.course_backend.domain.place.collection.entity.*;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.collection.scheduling.policy.CollectionPolicy;

@Component
public class CollectionExecutionState {
    private final CollectionReadiness readiness;
    private final CollectionPolicy policy;
    private final PlaceCollectionTempRepository temps;
    private final Clock clock;

    public CollectionExecutionState(CollectionReadiness readiness, CollectionPolicy policy,
            PlaceCollectionTempRepository temps, @Qualifier("collectionClock") Clock clock) {
        this.readiness = readiness;
        this.policy = policy;
        this.temps = temps;
        this.clock = clock;
    }

    public boolean canStartWork() {
        return readiness.isReady() && policy.canStartWork(ZonedDateTime.now(clock));
    }

    public boolean canCollectKakao() {
        return canStartWork() && backlog(PlaceCollectionStep.NAVER_ENRICHMENT) < policy.settings().getMaxNaverBacklog()
                && backlog(PlaceCollectionStep.AI_TAGGING) < policy.settings().getMaxAiBacklog();
    }

    public int naverCapacity() {
        return (int) Math.max(0, Math.min(policy.settings().getNaver().getBatchSize(),
                policy.settings().getMaxAiBacklog() - backlog(PlaceCollectionStep.AI_TAGGING)));
    }

    public boolean canSubmitAi() {
        if (!canStartWork()) return false;
        LocalDateTime resumeAt = temps.findAiQuotaResumeAt();
        return resumeAt == null || !resumeAt.isAfter(LocalDateTime.now());
    }

    private long backlog(PlaceCollectionStep step) {
        return temps.countBacklog(step);
    }
}
