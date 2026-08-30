ALTER TABLE place_collection_temp
    DROP CONSTRAINT ck_place_collection_temp_status;

ALTER TABLE place_collection_temp
    ADD CONSTRAINT ck_place_collection_temp_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'FAILED', 'COMPLETED'));
