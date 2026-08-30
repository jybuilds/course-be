UPDATE place_collection_temp
SET naver_search_url = NULL
WHERE naver_search_url IS NOT NULL
  AND naver_search_url NOT LIKE 'https://search.naver.com/search.naver?%';
