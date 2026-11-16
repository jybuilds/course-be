-- course-creation-data.sql 이 넣는 고정 ID 데이터와, 그 장소로 만든 코스를 모두 지운다.
DELETE FROM course_tags WHERE course_id IN (SELECT id FROM courses WHERE area_id IN (930001, 930002));
DELETE FROM course_items WHERE course_id IN (SELECT id FROM courses WHERE area_id IN (930001, 930002));
DELETE FROM course_stats WHERE course_id IN (SELECT id FROM courses WHERE area_id IN (930001, 930002));
DELETE FROM courses WHERE area_id IN (930001, 930002);
DELETE FROM place_tags WHERE place_id BETWEEN 931001 AND 931011;
DELETE FROM places WHERE id BETWEEN 931001 AND 931011;
DELETE FROM areas WHERE id IN (930001, 930002);
