-- 직접 코스 생성 테스트용 지역과 장소다. 앵커(931001)를 기준으로 거리를 의도적으로 벌려두었다.
INSERT INTO areas (
    id, city_id, code, name,
    collection_center_latitude, collection_center_longitude,
    collection_min_latitude, collection_min_longitude,
    collection_max_latitude, collection_max_longitude
) VALUES
    (930001, (SELECT id FROM cities WHERE code = 'SEOUL'), 'CREATION_SEOUL', '코스 생성 시뮬레이션 지역', 37.5445000, 127.0560000, 37.5300000, 127.0400000, 37.5900000, 127.0700000),
    (930002, (SELECT id FROM cities WHERE code = 'SEOUL'), 'CREATION_SEOUL_OTHER', '코스 생성 인접 지역', 37.5445000, 127.0600000, 37.5400000, 127.0580000, 37.5500000, 127.0620000);

-- 위도 0.001도는 약 111m다. 931005만 앵커에서 3km 밖에 둔다.
INSERT INTO places (
    id, provider, provider_place_id, area_id, place_type, name,
    latitude, longitude, is_anchor_candidate, is_active
) VALUES
    (931001, 'KAKAO', 'creation-anchor',          930001, 'ACTIVITY', '성수 복합문화공간', 37.5445000, 127.0560000, TRUE,  TRUE),
    (931002, 'KAKAO', 'creation-anchor-cafe',     930001, 'CAFE',     '성수 앵커 카페',    37.5445000, 127.0560000, TRUE,  TRUE),
    (931003, 'KAKAO', 'creation-activity-near',   930001, 'ACTIVITY', '가까운 팝업스토어', 37.5450000, 127.0560000, FALSE, TRUE),
    (931004, 'KAKAO', 'creation-activity-far',    930001, 'ACTIVITY', '먼 전시공간',       37.5545000, 127.0560000, FALSE, TRUE),
    (931005, 'KAKAO', 'creation-activity-out',    930001, 'ACTIVITY', '반경 밖 서점',      37.5845000, 127.0560000, FALSE, TRUE),
    (931006, 'KAKAO', 'creation-meal-near',       930001, 'MEAL',     '가까운 한식당',     37.5446000, 127.0561000, FALSE, TRUE),
    (931007, 'KAKAO', 'creation-meal-far',        930001, 'MEAL',     '먼 일식당',         37.5500000, 127.0560000, FALSE, TRUE),
    (931008, 'KAKAO', 'creation-cafe-near',       930001, 'CAFE',     '가까운 디저트 카페', 37.5447000, 127.0562000, FALSE, TRUE),
    (931009, 'KAKAO', 'creation-cafe-far',        930001, 'CAFE',     '먼 베이커리',       37.5460000, 127.0560000, FALSE, TRUE),
    (931010, 'KAKAO', 'creation-inactive-meal',   930001, 'MEAL',     '폐업한 식당',       37.5446000, 127.0560000, FALSE, FALSE),
    (931011, 'KAKAO', 'creation-other-area-cafe', 930002, 'CAFE',     '다른 Area 카페',    37.5446000, 127.0561000, FALSE, TRUE);

-- 먼 장소에 높은 태그 가중치를 줘서 태그가 거리를 뒤집는지 확인한다.
INSERT INTO place_tags (place_id, tag_id, weight) VALUES
    (931003, (SELECT id FROM tags WHERE code = 'POPUP'), 100),
    (931004, (SELECT id FROM tags WHERE code = 'EXHIBITION'), 100),
    (931006, (SELECT id FROM tags WHERE code = 'KOREAN_FOOD'), 100),
    (931007, (SELECT id FROM tags WHERE code = 'JAPANESE_FOOD'), 100),
    (931008, (SELECT id FROM tags WHERE code = 'DESSERT'), 100),
    (931009, (SELECT id FROM tags WHERE code = 'BREAD'), 100);
