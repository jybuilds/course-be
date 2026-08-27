ALTER TABLE course_stats
    ADD COLUMN impression_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE course_stats
    DROP CONSTRAINT ck_course_stats_counts_non_negative,
    ADD CONSTRAINT ck_course_stats_counts_non_negative
        CHECK (impression_count >= 0 AND view_count >= 0 AND selection_count >= 0 AND save_count >= 0 AND share_count >= 0);
