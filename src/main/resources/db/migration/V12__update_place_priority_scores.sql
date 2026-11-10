ALTER TABLE places
    DROP CONSTRAINT places_priority_experience_check,
    DROP CONSTRAINT places_priority_distinctiveness_check,
    DROP COLUMN priority_outing_demand,
    ADD CHECK (priority_experience BETWEEN 0 AND 50),
    ADD CHECK (priority_distinctiveness BETWEEN 0 AND 50);
