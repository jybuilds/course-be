ALTER TABLE place_collection_temp
    RENAME COLUMN naver_place_url TO naver_search_url;

ALTER TABLE places
    ADD COLUMN naver_search_url VARCHAR(1000);
