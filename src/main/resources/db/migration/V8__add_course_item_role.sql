ALTER TABLE places
    DROP CONSTRAINT ck_places_place_type;

UPDATE places
SET place_type = 'MEAL'
WHERE place_type = 'FOOD';

ALTER TABLE places
    ADD CONSTRAINT ck_places_place_type
        CHECK (place_type IN ('ACTIVITY', 'MEAL', 'CAFE'));

ALTER TABLE course_items
    ADD COLUMN item_role VARCHAR(20);

UPDATE course_items AS item
SET item_role = place.place_type
FROM places AS place
WHERE item.place_id = place.id;

ALTER TABLE course_items
    ALTER COLUMN item_role SET NOT NULL,
    ADD CONSTRAINT ck_course_items_item_role
        CHECK (item_role IN ('ACTIVITY', 'MEAL', 'CAFE'));
