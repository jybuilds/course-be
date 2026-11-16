package com.junsang.course_backend.domain.place.collection.pipeline.ai.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.repository.OpenAiBatchTaggingJobRepository;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import com.junsang.course_backend.infra.openai.config.AiProviderProperties;
import com.junsang.course_backend.domain.place.collection.pipeline.support.error.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// OpenAI Batch Job 생성과 Temp 선점을 하나의 트랜잭션으로 처리한다.
@Service
@RequiredArgsConstructor
public class OpenAiBatchJobWriter {

    private final JdbcTemplate jdbc;
    private final AiProviderProperties properties;
    private final PlaceCollectionTempRepository tempRepository;
    private final OpenAiBatchTaggingJobRepository jobRepository;

    // 신규 대기와 예약된 재시도 장소를 잠근 뒤 하나의 Batch에 선점한다.
    @Transactional
    public BatchClaim claimPending(int limit) {
        List<PlaceCollectionTemp> targets = tempRepository.findAiBatchSubmissionTargets(
                PlaceCollectionStep.AI_TAGGING,
                PlaceCollectionTempStatus.PENDING,
                PlaceCollectionTempStatus.FAILED,
                retryableSubmissionErrorCodes(),
                LocalDateTime.now(),
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
    public void fail(
            Long jobId,
            PlaceRefinementErrorCode errorCode,
            String errorMessage
    ) {
        fail(jobId, new CollectionFailure(errorCode, errorMessage));
    }

    @Transactional
    public void fail(Long jobId, CollectionFailure failure) {
        // 제출 실패의 Job·manifest·Temp·재시도 예약은 함께 저장한다.
        failPending(jobId, failure);
        OpenAiBatchTaggingJob job = find(jobId);
        job.fail(failure.code(), failure.message());
        jobRepository.save(job);
    }

    @Transactional
    public void failItem(Long jobId, Long tempId, CollectionFailure failure) {
        List<String> states = jdbc.queryForList("SELECT status FROM ai_batch_tagging_items WHERE job_id=? AND temp_id=? FOR UPDATE",
                String.class, jobId, tempId);
        if (states.isEmpty() || !"PENDING".equals(states.getFirst())) return;
        tempRepository.findByIdForUpdate(tempId).filter(temp -> jobId.equals(temp.getAiBatchJobId())).ifPresent(temp -> {
            temp.fail(failure.code(), failure.message());
            temp.scheduleRetry(CollectionErrorPolicy.nextRetry(failure, temp.getAiRetryCount(), LocalDateTime.now()));
        });
        jdbc.update("UPDATE ai_batch_tagging_items SET status='FAILED', error_code=?, updated_at=CURRENT_TIMESTAMP WHERE job_id=? AND temp_id=?",
                failure.code().name(), jobId, tempId);
    }

    @Transactional
    public void failPending(Long jobId, CollectionFailure failure) {
        for (Item item : items(jobId)) if ("PENDING".equals(item.status())) failItem(jobId, item.tempId(), failure);
        // 이전 버전의 manifest 없는 작업도 오류 상태와 운영 확인 사유를 남긴다.
        for (PlaceCollectionTemp temp : tempRepository.findByAiBatchJobIdOrderById(jobId)) {
            temp.fail(failure.code(), failure.message());
            temp.scheduleRetry(CollectionErrorPolicy.nextRetry(failure, temp.getAiRetryCount(), LocalDateTime.now()));
        }
    }

    public record Item(Long tempId, String requestKey, String status) { }

    @Transactional(readOnly = true)
    public List<Item> items(Long jobId) {
        return jdbc.query("SELECT temp_id, request_key, status FROM ai_batch_tagging_items WHERE job_id=? ORDER BY temp_id",
                (row, n) -> new Item(row.getLong(1), row.getString(2), row.getString(3)), jobId);
    }

    @Transactional
    public String acquireCollection(Long jobId) {
        String token = UUID.randomUUID().toString();

        int count = jdbc.update(
                "UPDATE openai_batch_tagging_jobs SET collect_token=?, "
                        + "collect_lease_until=CURRENT_TIMESTAMP + INTERVAL '15 minutes', "
                        + "last_polled_at=CURRENT_TIMESTAMP "
                        + "WHERE id=? AND status='SUBMITTED' "
                        + "AND (collect_lease_until IS NULL OR collect_lease_until < CURRENT_TIMESTAMP)",
                token, jobId
        );

        return count == 1 ? token : null;
    }

    @Transactional
    public void renewCollection(Long jobId, String token) {
        if (jdbc.update("UPDATE openai_batch_tagging_jobs SET collect_lease_until=CURRENT_TIMESTAMP + INTERVAL '15 minutes' WHERE id=? AND collect_token=?",
                jobId, token) != 1) throw new CollectionLeaseLostException();
    }

    public static class CollectionLeaseLostException extends IllegalStateException {
        public CollectionLeaseLostException() {
            super("AI 수집 소유권 만료");
        }
    }

    private void checkOwner(Long jobId, String token) {
        String owner = jdbc.queryForObject("SELECT collect_token FROM openai_batch_tagging_jobs WHERE id=? FOR UPDATE", String.class, jobId);
        if (!token.equals(owner)) throw new CollectionLeaseLostException();
    }

    @Transactional
    public void failItem(Long jobId, String token, Long tempId, CollectionFailure failure) {
        checkOwner(jobId, token);
        failItem(jobId, tempId, failure);
    }

    @Transactional
    public void failPending(Long jobId, String token, CollectionFailure failure) {
        checkOwner(jobId, token);
        failPending(jobId, failure);
    }

    @Transactional
    public void releaseCollection(Long jobId, String token) {
        jdbc.update("UPDATE openai_batch_tagging_jobs SET collect_token=NULL, collect_lease_until=NULL WHERE id=? AND collect_token=?", jobId, token);
    }

    @Transactional
    public void recordPoll(Long jobId, String token, String remoteStatus) {
        jdbc.update("UPDATE openai_batch_tagging_jobs SET last_polled_at=CURRENT_TIMESTAMP, remote_status=?, error_code=NULL, error_message=NULL WHERE id=? AND collect_token=?",
                remoteStatus, jobId, token);
    }

    @Transactional
    public void pausePoll(Long jobId, String token, CollectionFailure failure) {
        checkOwner(jobId, token);
        jdbc.update("UPDATE openai_batch_tagging_jobs SET error_code=?,error_message=? WHERE id=? AND collect_token=?",
                failure.code().name(), failure.message(), jobId, token);
    }

    @Transactional
    public void settle(Long jobId, String token) {
        renewCollection(jobId, token);
        List<Item> items = items(jobId);
        if (items.stream().anyMatch(item -> "PENDING".equals(item.status()))) throw new IllegalStateException("미반영 AI 결과 존재");
        OpenAiBatchTaggingJob job = find(jobId);
        if (items.stream().anyMatch(item -> "FAILED".equals(item.status()))) {
            List<String> errors = jdbc.queryForList("SELECT DISTINCT error_code FROM ai_batch_tagging_items WHERE job_id=? AND status='FAILED'",
                    String.class, jobId);
            job.fail(errors.size() == 1 ? PlaceRefinementErrorCode.valueOf(errors.getFirst()) : PlaceRefinementErrorCode.AI_BATCH_PARTIAL_FAILURE,
                    "실패 장소 존재: 상세 사유는 Temp 및 ai_batch_tagging_items 확인");
        } else job.complete();
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

        // 원격 호출 전에 Job ID를 먼저 발급해 동시 제출 시 같은 Temp가 두 Batch에 들어가는 일을 막는다.
        OpenAiBatchTaggingJob job = OpenAiBatchTaggingJob.submitting(targets.size());
        job.configureProvider(properties.provider().toUpperCase(java.util.Locale.ROOT), properties.selected().model());
        jobRepository.save(job);
        for (int start = 0; start < targets.size(); start += 10) {
            List<PlaceCollectionTemp> chunk = targets.subList(start, Math.min(start + 10, targets.size()));
            String key = "places-" + chunk.getFirst().getId() + "-" + chunk.getLast().getId();
            for (PlaceCollectionTemp temp : chunk) jdbc.update(
                    "INSERT INTO ai_batch_tagging_items(job_id,temp_id,request_key) VALUES (?,?,?)", job.getId(), temp.getId(), key);
        }
        targets.forEach(target -> {
            target.startAiBatchTagging(job.getId());
        });
        tempRepository.saveAll(targets);
        return new BatchClaim(job.getId(), targets);
    }

    // 원격 요청 자체가 끝나지 않은 경우만 새 Batch 제출 대상으로 다시 잡는다.
    private List<PlaceRefinementErrorCode> retryableSubmissionErrorCodes() {
        return new ArrayList<>(CollectionErrorPolicy.AI_RETRYABLE);
    }

    public record BatchClaim(Long jobId, List<PlaceCollectionTemp> targets) {
    }
}
