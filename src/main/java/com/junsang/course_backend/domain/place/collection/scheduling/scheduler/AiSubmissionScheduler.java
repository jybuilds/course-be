package com.junsang.course_backend.domain.place.collection.scheduling.scheduler;

import com.junsang.course_backend.domain.place.collection.pipeline.support.error.CollectionErrorPolicy;
import java.util.List;

import com.junsang.course_backend.domain.place.collection.budget.CollectionDeferredException;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.entity.OpenAiBatchTaggingStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.repository.OpenAiBatchTaggingJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.OpenAiBatchTaggingService;

import com.junsang.course_backend.domain.place.collection.scheduling.policy.CollectionPolicy;
import com.junsang.course_backend.domain.place.collection.scheduling.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("aiSubmissionWorker")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="place-collection.scheduling.enabled", havingValue="true")
public class AiSubmissionScheduler {
    private final CollectionPolicy policy;
    private final OpenAiBatchTaggingService service;
    private final OpenAiBatchTaggingJobRepository jobs;
    private final CollectionExecutionState state;

    @Scheduled(fixedDelayString="#{@collectionPolicy.settings().aiSubmission.intervalMillis}", scheduler="aiSubmissionScheduler")
    public void run() {
        try {
            execute();
        } catch (CollectionDeferredException waiting) {
            log.debug("수집 작업 대기");
        } catch (RuntimeException error) {
            log.warn("AiSubmissionScheduler 실행 실패: {}", error.getClass().getSimpleName());
        }
    }

    private void execute() {
        if (!state.canSubmitAi()) return;
        if (jobs.existsByStatusAndErrorCodeIn(OpenAiBatchTaggingStatus.FAILED,
                CollectionErrorPolicy.POLL_BLOCKED)) return;
        long active = jobs.countByStatusIn(List.of(
                OpenAiBatchTaggingStatus.SUBMITTING,
                OpenAiBatchTaggingStatus.SUBMITTED));
        if (active < policy.settings().getMaxActiveBatches()) service.submitPending(policy.settings().getAiSubmission().getBatchSize());
    }
}
