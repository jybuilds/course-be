ALTER TABLE area_tag_anchors RENAME TO area_tag_anchors_legacy;

CREATE TABLE area_tag_anchors (
    area_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT pk_area_tag_anchors_by_place PRIMARY KEY (area_id, tag_id, place_id),
    CONSTRAINT fk_area_tag_anchors_area_tag FOREIGN KEY (area_id, tag_id)
        REFERENCES area_tags (area_id, tag_id) ON DELETE CASCADE
);

INSERT INTO area_tag_anchors (area_id, tag_id, place_id)
SELECT at.area_id, at.tag_id, p.id
FROM area_tag_anchors_legacy legacy
JOIN area_tags at ON at.id = legacy.area_tag_id
JOIN places p ON p.provider = legacy.provider
             AND p.provider_place_id = legacy.provider_place_id;

-- Preserve the original mappings in environments where anchor places are missing.
DO $$
BEGIN
    IF (SELECT count(*) FROM area_tag_anchors) = (SELECT count(*) FROM area_tag_anchors_legacy) THEN
        DROP TABLE area_tag_anchors_legacy;
    END IF;
END $$;

CREATE INDEX idx_area_tag_anchors_place ON area_tag_anchors (place_id);
