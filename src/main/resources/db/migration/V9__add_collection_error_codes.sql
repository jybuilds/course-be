ALTER TABLE place_collection_jobs
    ADD COLUMN error_code VARCHAR(100);

ALTER TABLE openai_batch_tagging_jobs
    ADD COLUMN error_code VARCHAR(100);

ALTER TABLE place_collection_temp
    ADD COLUMN naver_blog_error_code VARCHAR(100);
