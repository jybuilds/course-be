ALTER TABLE places
    ADD COLUMN priority_score INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_places_priority_score_non_negative CHECK (priority_score >= 0);
