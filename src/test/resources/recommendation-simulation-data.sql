INSERT INTO areas (
    id, city_id, code, name,
    collection_center_latitude, collection_center_longitude,
    collection_min_latitude, collection_min_longitude,
    collection_max_latitude, collection_max_longitude
) VALUES
    (910001, (SELECT id FROM cities WHERE code = 'SEOUL'), 'SIMULATION_SEOUL', '서울 추천 시뮬레이션 지역', 37.5500000, 127.0000000, 37.5400000, 126.9900000, 37.5600000, 127.0100000),
    (910002, (SELECT id FROM cities WHERE code = 'DAEJEON'), 'SIMULATION_DAEJEON', '대전 추천 시뮬레이션 지역', 36.3300000, 127.4300000, 36.3200000, 127.4200000, 36.3400000, 127.4400000);

INSERT INTO places (
    id, provider, provider_place_id, area_id, place_type, name,
    latitude, longitude, is_anchor_candidate, is_active
) VALUES
    (911001, 'KAKAO', 'simulation-anchor-seongsu', 910001, 'ACTIVITY', '성수 복합문화공간', 37.5445000, 127.0560000, TRUE, TRUE),
    (911002, 'KAKAO', 'simulation-anchor-yeouido', 910001, 'ACTIVITY', '여의도 쇼핑몰', 37.5259000, 126.9284000, TRUE, TRUE),
    (911003, 'KAKAO', 'simulation-anchor-seochon', 910001, 'ACTIVITY', '서촌 전시공간', 37.5790000, 126.9700000, TRUE, TRUE),
    (911004, 'KAKAO', 'simulation-anchor-hangang', 910001, 'ACTIVITY', '한강공원', 37.5283000, 126.9326000, TRUE, TRUE),
    (911005, 'KAKAO', 'simulation-anchor-coex', 910001, 'ACTIVITY', '코엑스', 37.5117000, 127.0592000, TRUE, TRUE),
    (911006, 'KAKAO', 'simulation-seongsu-bakery', 910001, 'CAFE', '성수 베이커리', 37.5450000, 127.0570000, FALSE, TRUE),
    (911007, 'KAKAO', 'simulation-seongsu-popup', 910001, 'ACTIVITY', '성수 팝업스토어', 37.5455000, 127.0580000, FALSE, TRUE),
    (911008, 'KAKAO', 'simulation-yeouido-bakery', 910001, 'CAFE', '여의도 베이커리', 37.5260000, 126.9290000, FALSE, TRUE),
    (911009, 'KAKAO', 'simulation-seochon-bakery', 910001, 'CAFE', '서촌 베이커리', 37.5795000, 126.9710000, FALSE, TRUE),
    (911010, 'KAKAO', 'simulation-coex-bakery', 910001, 'CAFE', '코엑스 베이커리', 37.5120000, 127.0600000, FALSE, TRUE),
    (912001, 'KAKAO', 'simulation-sungsimdang', 910002, 'CAFE', '성심당', 36.3286000, 127.4273000, TRUE, TRUE),
    (912002, 'KAKAO', 'simulation-sungsimdang-boutique', 910002, 'CAFE', '성심당 부티크', 36.3290000, 127.4280000, FALSE, TRUE),
    (912003, 'KAKAO', 'simulation-daejeon-bakery', 910002, 'CAFE', '대전 동네 빵집', 36.3300000, 127.4290000, FALSE, TRUE),
    (912004, 'KAKAO', 'simulation-daejeon-meal', 910002, 'MEAL', '대전 한식당', 36.3310000, 127.4300000, FALSE, TRUE),
    (912005, 'KAKAO', 'simulation-dream-shop', 910002, 'ACTIVITY', '꿈돌이 편집샵', 36.3320000, 127.4310000, FALSE, TRUE);

INSERT INTO place_tags (place_id, tag_id, weight) VALUES
    (911001, (SELECT id FROM tags WHERE code = 'SHOPPING'), 90),
    (911002, (SELECT id FROM tags WHERE code = 'SHOPPING'), 95),
    (911003, (SELECT id FROM tags WHERE code = 'EXHIBITION'), 100),
    (911004, (SELECT id FROM tags WHERE code = 'WALK'), 100),
    (911005, (SELECT id FROM tags WHERE code = 'SHOPPING'), 70),
    (911006, (SELECT id FROM tags WHERE code = 'BREAD'), 100),
    (911007, (SELECT id FROM tags WHERE code = 'POPUP'), 100),
    (911008, (SELECT id FROM tags WHERE code = 'BREAD'), 100),
    (911009, (SELECT id FROM tags WHERE code = 'BREAD'), 100),
    (911010, (SELECT id FROM tags WHERE code = 'BREAD'), 50),
    (912001, (SELECT id FROM tags WHERE code = 'BREAD'), 100),
    (912002, (SELECT id FROM tags WHERE code = 'BREAD'), 100),
    (912003, (SELECT id FROM tags WHERE code = 'BREAD'), 100),
    (912004, (SELECT id FROM tags WHERE code = 'KOREAN_FOOD'), 100),
    (912005, (SELECT id FROM tags WHERE code = 'SHOPPING'), 100);

INSERT INTO courses (id, area_id, anchor_place_id, title, description, is_published) VALUES
    (920001, 910001, 911001, '성수 쇼핑 빵 데이트', '쇼핑과 빵을 함께 즐기는 연인 코스', TRUE),
    (920002, 910001, 911001, '성수 쇼핑 집중 코스', '인기 있는 쇼핑 중심 코스', TRUE),
    (920003, 910001, 911002, '여의도 쇼핑 빵 코스', '쇼핑과 빵을 함께 즐기는 가족 코스', TRUE),
    (920004, 910001, 911003, '서촌 빵 전시 코스', '빵과 전시 중심의 오전 코스', TRUE),
    (920005, 910001, 911004, '한강 인기 산책 코스', '태그보다 인기도가 높은 비교 코스', TRUE),
    (920006, 910001, 911005, '코엑스 실내 쇼핑 코스', '상황 정보가 없는 신규 코스', TRUE),
    (920007, 910002, 912001, '대전 빵 투어', '빵집 세 곳과 식사, 편집샵을 방문하는 코스', TRUE);

INSERT INTO course_items (course_id, place_id, item_role, item_order) VALUES
    (920001, 911001, 'ACTIVITY', 0),
    (920001, 911006, 'ACTIVITY', 1),
    (920002, 911001, 'ACTIVITY', 0),
    (920002, 911007, 'ACTIVITY', 1),
    (920003, 911002, 'ACTIVITY', 0),
    (920003, 911008, 'CAFE', 1),
    (920004, 911003, 'ACTIVITY', 0),
    (920004, 911009, 'CAFE', 1),
    (920005, 911004, 'ACTIVITY', 0),
    (920006, 911005, 'ACTIVITY', 0),
    (920006, 911010, 'CAFE', 1),
    (920007, 912001, 'ACTIVITY', 0),
    (920007, 912002, 'ACTIVITY', 1),
    (920007, 912003, 'ACTIVITY', 2),
    (920007, 912004, 'MEAL', 3),
    (920007, 912005, 'ACTIVITY', 4);

INSERT INTO course_companion_types (course_id, companion_type) VALUES
    (920001, 'LOVER'),
    (920002, 'LOVER'),
    (920003, 'FAMILY'),
    (920004, 'LOVER'),
    (920005, 'LOVER');

INSERT INTO course_time_slots (course_id, time_slot) VALUES
    (920001, 'AFTERNOON'),
    (920002, 'AFTERNOON'),
    (920003, 'AFTERNOON'),
    (920004, 'MORNING'),
    (920005, 'AFTERNOON');

INSERT INTO course_stats (course_id, impression_count, selection_count) VALUES
    (920001, 3, 50),
    (920002, 9, 300),
    (920003, 1, 200),
    (920004, 0, 15),
    (920005, 7, 1000);
