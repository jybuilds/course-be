ALTER TABLE place_collection_jobs
    DROP CONSTRAINT ck_collection_jobs_status;

ALTER TABLE place_collection_jobs
    ADD CONSTRAINT ck_collection_jobs_status CHECK (
        status IN ('READY', 'RUNNING', 'COMPLETED', 'PARTIAL', 'SPLIT', 'FAILED')
    );
