package com.junsang.course_backend.domain.place.collection.scheduling.scheduler;

import java.time.LocalDateTime;

import com.junsang.course_backend.domain.place.collection.budget.CollectionDeferredException;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.entity.OpenAiBatchTaggingStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.repository.OpenAiBatchTaggingJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.OpenAiBatchTaggingService;
import com.junsang.course_backend.domain.place.collection.pipeline.support.error.CollectionErrorPolicy;

import com.junsang.course_backend.domain.place.collection.scheduling.policy.CollectionPolicy;
import com.junsang.course_backend.domain.place.collection.scheduling.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("aiResultWorker")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="place-collection.scheduling.enabled", havingValue="true")
public class AiResultScheduler {
    private final CollectionPolicy policy;
    private final OpenAiBatchTaggingService service;
    private final OpenAiBatchTaggingJobRepository jobs;
    private final CollectionReadiness readiness;

    @Scheduled(fixedDelayString="#{@collectionPolicy.settings().aiResult.intervalMillis}", scheduler="aiResultScheduler")
    public void run() {
        try {
            execute();
        } catch (CollectionDeferredException waiting) {
            log.debug("수집 작업 대기");
        } catch (RuntimeException error) {
            log.warn("AiResultScheduler 실행 실패: {}", error.getClass().getSimpleName());
        }
    }

    private void execute() {
        if (!readiness.isReady()) return;
        var targets = jobs.findPollableJobs(
                OpenAiBatchTaggingStatus.SUBMITTED,
                CollectionErrorPolicy.POLL_BLOCKED,
                LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, policy.settings().getAiResult().getBatchSize()));
        for (var job : targets) {
            try { service.collect(job.getId()); }
            catch (RuntimeException error) { log.warn("AI 결과 회수 실패: jobId={}, type={}", job.getId(), error.getClass().getSimpleName()); }
        }
    }
}
