CREATE TABLE collection_credential_blocks (
    api_scope VARCHAR(30) NOT NULL,
    credential_id VARCHAR(100) NOT NULL,
    blocked_until TIMESTAMPTZ,
    reason VARCHAR(30) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (api_scope, credential_id)
);
COMMENT ON TABLE collection_credential_blocks IS '인증 정보별 공급자 대기 상태. 키 원문과 사용량 추정치는 저장하지 않음';
