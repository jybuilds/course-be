UPDATE place_collection_temp
SET naver_search_url = NULL
WHERE naver_search_url LIKE 'https://search.naver.com/search.naver?%';

UPDATE places
SET naver_search_url = NULL
WHERE naver_search_url LIKE 'https://search.naver.com/search.naver?%';
