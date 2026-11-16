-- make course-show 로 실행한다. 코스 생성 테스트 데이터가 DB에 실제로 어떻게 들어갔는지 표로 보여준다.

\echo
\echo '■ 테스트 지역'
SELECT id, code AS 코드, name AS 이름
FROM areas
WHERE id IN (930001, 930002)
ORDER BY id;

\echo '■ 테스트 장소'
SELECT p.id,
       p.name AS 이름,
       p.place_type AS 유형,
       a.name AS 지역,
       CASE WHEN p.is_active THEN '영업' ELSE '폐업' END AS 상태,
       CASE WHEN p.is_anchor_candidate THEN '예' ELSE '' END AS 앵커후보,
       COALESCE(string_agg(t.code || ' ' || pt.weight, ', ' ORDER BY t.code), '(태그 없음)') AS 태그
FROM places p
JOIN areas a ON a.id = p.area_id
LEFT JOIN place_tags pt ON pt.place_id = p.id
LEFT JOIN tags t ON t.id = pt.tag_id
WHERE p.id BETWEEN 931001 AND 931011
GROUP BY p.id, a.name
ORDER BY p.id;

\echo '■ 만들어진 코스'
\echo '  (비어 있으면 make run 상태에서 make course-check 를 실행한 뒤 다시 보세요)'
SELECT c.id,
       c.title AS 제목,
       ap.name AS 앵커,
       CASE WHEN c.is_published THEN '공개' ELSE '비공개' END AS 공개,
       (SELECT string_agg(ci.item_order || '. ' || p.name || ' ' || ci.item_role, '  →  ' ORDER BY ci.item_order)
          FROM course_items ci
          JOIN places p ON p.id = ci.place_id
         WHERE ci.course_id = c.id) AS 일정,
       (SELECT string_agg(t.code || ' ' || ct.weight, ', ' ORDER BY ct.weight DESC, t.code)
          FROM course_tags ct
          JOIN tags t ON t.id = ct.tag_id
         WHERE ct.course_id = c.id) AS 코스태그
FROM courses c
JOIN places ap ON ap.id = c.anchor_place_id
WHERE c.area_id IN (930001, 930002)
ORDER BY c.id;
