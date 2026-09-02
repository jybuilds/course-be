package com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.service;

import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.entity.OpenAiBatchTaggingStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.repository.OpenAiBatchTaggingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// 제출된 OpenAI Batch 결과를 주기적으로 수집한다.
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiBatchTaggingScheduler {

    private final OpenAiBatchTaggingJobRepository jobRepository;
    private final OpenAiBatchTaggingService openAiBatchTaggingService;

    // 원격 완료·실패를 확인해 Temp가 PROCESSING 상태에 멈지 않도록 한다.
    @Scheduled(fixedDelayString = "${place-collection.ai.batch.poll-interval-millis:60000}")
    public void collectSubmittedBatches() {
        jobRepository.findByStatus(OpenAiBatchTaggingStatus.SUBMITTED).forEach(job -> {
            try {
                openAiBatchTaggingService.collect(job.getId());
            } catch (RuntimeException exception) {
                // 일시적인 조회 실패는 SUBMITTED를 유지해 다음 주기에 다시 확인한다.
                log.warn("OpenAI Batch 결과 확인 실패: jobId={}", job.getId(), exception);
            }
        });
    }
}
