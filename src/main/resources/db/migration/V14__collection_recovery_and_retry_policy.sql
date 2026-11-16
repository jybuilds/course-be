ALTER TABLE place_collection_temp ADD COLUMN next_retry_at TIMESTAMP;
ALTER TABLE place_collection_jobs ADD COLUMN collection_run_key VARCHAR(36);
ALTER TABLE place_collection_jobs ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE place_collection_jobs ADD COLUMN next_retry_at TIMESTAMP;
ALTER TABLE openai_batch_tagging_jobs ADD COLUMN provider VARCHAR(30);
ALTER TABLE openai_batch_tagging_jobs ADD COLUMN model VARCHAR(100);
ALTER TABLE openai_batch_tagging_jobs ADD COLUMN collect_token VARCHAR(36);
ALTER TABLE openai_batch_tagging_jobs ADD COLUMN collect_lease_until TIMESTAMP;
ALTER TABLE openai_batch_tagging_jobs ADD COLUMN last_polled_at TIMESTAMP;
ALTER TABLE openai_batch_tagging_jobs ADD COLUMN remote_status VARCHAR(30);
CREATE TABLE ai_batch_tagging_items (
    job_id BIGINT NOT NULL REFERENCES openai_batch_tagging_jobs(id),
    temp_id BIGINT NOT NULL REFERENCES place_collection_temp(id),
    request_key VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    error_code VARCHAR(100),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (job_id, temp_id)
);
CREATE INDEX idx_ai_batch_items_request ON ai_batch_tagging_items (job_id, request_key, status);
CREATE INDEX idx_temp_retry_due ON place_collection_temp (next_retry_at, id) WHERE status = 'FAILED';
CREATE INDEX idx_kakao_retry_due ON place_collection_jobs (next_retry_at, id) WHERE status = 'FAILED';
-- 과거 데이터에서 의미가 확실한 일시 실패만 기존 시간 기준으로 재시도 예약한다.
UPDATE place_collection_temp
SET next_retry_at = COALESCE(last_attempt_at, updated_at)
    + CASE WHEN ai_retry_count < 3 THEN INTERVAL '5 minutes' ELSE INTERVAL '24 hours' END
WHERE processing_step = 'AI_TAGGING' AND status = 'FAILED'
  AND error_code IN ('OPENAI_BATCH_TEMPORARY_FAILURE', 'AI_RESPONSE_PARSE_FAILED',
      'AI_RESPONSE_TARGET_MISMATCH', 'AI_TAG_VALIDATION_FAILED', 'AI_BATCH_OUTPUT_INVALID', 'AI_RESPONSE_INVALID');
UPDATE place_collection_temp SET next_retry_at = COALESCE(last_attempt_at, updated_at) + INTERVAL '5 minutes'
WHERE processing_step = 'NAVER_ENRICHMENT' AND status = 'FAILED' AND attempt_count < 3
  AND error_code IN ('NAVER_API_TEMPORARY_FAILURE', 'NAVER_API_RATE_LIMITED');
UPDATE place_collection_jobs SET next_retry_at = CURRENT_TIMESTAMP
WHERE status = 'FAILED' AND error_code = 'COLLECTION_INTERRUPTED';
