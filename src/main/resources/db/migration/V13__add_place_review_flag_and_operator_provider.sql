ALTER TABLE places
    ADD COLUMN needs_review BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE places
    DROP CONSTRAINT ck_places_provider;

ALTER TABLE places
    ADD CONSTRAINT ck_places_provider CHECK (provider IN ('KAKAO', 'TOUR', 'OPERATOR'));
