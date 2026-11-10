package com.junsang.course_backend.domain.place.collection.pipeline.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "place-collection.recovery.single-instance", havingValue = "true")
public class PlaceCollectionRestartRecovery implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // shortcut: 수집 앱이 하나일 때만 시작 시 남은 작업을 회수한다. 여러 인스턴스에서는 소유권과 만료 시간을 사용한다.
        int kakao = jdbc.update("""
                UPDATE place_collection_jobs
                SET status = 'FAILED', error_code = 'COLLECTION_INTERRUPTED',
                    error_message = 'Interrupted before collection finished',
                    completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE status IN ('READY', 'RUNNING')
                """);
        int naver = jdbc.update("""
                UPDATE place_collection_temp
                SET status = 'PENDING', error_code = NULL, error_message = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE processing_step = 'NAVER_ENRICHMENT' AND status = 'PROCESSING'
                """);

        // ID가 있는 원격 작업은 기존 결과 조회 스케줄러가 이어받는다.
        int resumable = jdbc.update("""
                UPDATE openai_batch_tagging_jobs
                SET status = 'SUBMITTED', updated_at = CURRENT_TIMESTAMP
                WHERE status = 'SUBMITTING' AND openai_batch_id IS NOT NULL
                """);
        int unknown = jdbc.update("""
                UPDATE openai_batch_tagging_jobs
                SET status = 'FAILED', error_code = 'OPENAI_BATCH_SUBMISSION_UNKNOWN',
                    error_message = 'Check remote submission before retrying',
                    updated_at = CURRENT_TIMESTAMP
                WHERE status = 'SUBMITTING' AND openai_batch_id IS NULL
                """);
        // 로컬 작업 연결을 보존해 원격 배치와 대조할 수 있게 한다.
        int aiTargets = jdbc.update("""
                UPDATE place_collection_temp temp
                SET status = 'FAILED', error_code = 'OPENAI_BATCH_SUBMISSION_UNKNOWN',
                    error_message = 'Check remote submission before retrying',
                    updated_at = CURRENT_TIMESTAMP
                FROM openai_batch_tagging_jobs job
                WHERE temp.ai_batch_job_id = job.id
                  AND temp.processing_step = 'AI_TAGGING' AND temp.status = 'PROCESSING'
                  AND job.status = 'FAILED' AND job.error_code = 'OPENAI_BATCH_SUBMISSION_UNKNOWN'
                """);
        log.info("Collection restart recovery: kakao={}, naver={}, aiResumable={}, aiUnknown={}, aiTargets={}",
                kakao, naver, resumable, unknown, aiTargets);
    }
}
