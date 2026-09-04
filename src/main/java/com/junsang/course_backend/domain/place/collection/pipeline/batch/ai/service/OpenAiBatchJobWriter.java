package com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.repository.OpenAiBatchTaggingJobRepository;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// OpenAI Batch Job 생성과 Temp 선점을 하나의 트랜잭션으로 처리한다.
@Service
@RequiredArgsConstructor
public class OpenAiBatchJobWriter {

    private final PlaceCollectionTempRepository tempRepository;
    private final OpenAiBatchTaggingJobRepository jobRepository;

    // 전체 대기열에서 잠근 Temp를 Batch Job에 선점한다.
    @Transactional
    public BatchClaim claimPending(int limit) {
        List<PlaceCollectionTemp> targets = tempRepository.findAiBatchSubmissionTargets(
                PlaceCollectionStep.AI_TAGGING,
                PlaceCollectionTempStatus.PENDING,
                PlaceCollectionTempStatus.FAILED,
                PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED,
                PageRequest.of(0, limit)
        );
        return claim(targets);
    }

    // 지정 Area의 잠근 Temp를 Batch Job에 선점한다.
    @Transactional
    public BatchClaim claimPendingByArea(Long areaId, int limit) {
        List<PlaceCollectionTemp> targets = tempRepository.findAiBatchSubmissionTargetsByAreaId(
                areaId,
                PlaceCollectionStep.AI_TAGGING,
                PlaceCollectionTempStatus.PENDING,
                PlaceCollectionTempStatus.FAILED,
                PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED,
                PageRequest.of(0, limit)
        );
        return claim(targets);
    }

    // 원격 Batch ID를 저장하고 결과 대기 상태로 전환한다.
    @Transactional
    public void markSubmitted(Long jobId, String openAiBatchId) {
        OpenAiBatchTaggingJob job = find(jobId);
        job.submit(openAiBatchId);
        jobRepository.save(job);
    }

    // 원격 제출·처리 실패 시 Temp와 Job을 함께 실패 처리한다.
    @Transactional
    public void fail(Long jobId, String errorMessage) {
        List<PlaceCollectionTemp> targets = tempRepository.findByAiBatchJobIdOrderById(jobId);
        targets.forEach(target -> target.fail(
                PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED,
                errorMessage
        ));
        tempRepository.saveAll(targets);

        OpenAiBatchTaggingJob job = find(jobId);
        job.fail(errorMessage);
        jobRepository.save(job);
    }

    // 결과 반영이 끝난 Job을 완료 처리한다.
    @Transactional
    public void complete(Long jobId) {
        OpenAiBatchTaggingJob job = find(jobId);
        job.complete();
        jobRepository.save(job);
    }

    // 원격 상태 조회에 필요한 Job 정보를 읽는다.
    @Transactional(readOnly = true)
    public OpenAiBatchTaggingJob find(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("AI Batch Job을 찾을 수 없습니다: " + jobId));
    }

    private BatchClaim claim(List<PlaceCollectionTemp> targets) {
        if (targets.isEmpty()) {
            return null;
        }
        OpenAiBatchTaggingJob job = jobRepository.save(OpenAiBatchTaggingJob.submitting(targets.size()));
        targets.forEach(target -> target.startAiBatchTagging(job.getId()));
        tempRepository.saveAll(targets);
        return new BatchClaim(job.getId(), targets);
    }

    public record BatchClaim(Long jobId, List<PlaceCollectionTemp> targets) {
    }
}
