# 직접 코스 생성 — 수동 테스트 가이드

> 대상 브랜치: `feature/course-creation`
>
> **바쁘면 이것만:** `make course-seed` → `make run` → (새 터미널) `make course-check` → `make course-show`로 DB 확인 → 끝나면 `make course-clean`
>
> 이 문서는 코드를 모르는 상태에서도 위에서부터 그대로 따라 하면 직접 코스 생성 API를
> 처음부터 끝까지 확인할 수 있도록 만들었다. 각 단계는 **무엇을 하는지**와 **실행할 것**,
> **이렇게 나오면 정상**으로 나뉜다.

---

## 0. 준비물 확인

### 무엇을 하나

로컬에 Java 21과 Docker가 준비돼 있는지 확인한다. Java 8만 있으면 Gradle이 아예 뜨지 않는다.

### 이거 하셈

```bash
java -version && docker info --format 'docker={{.ServerVersion}}'
```

### 이렇게 나오면 정상

```
openjdk version "21.x.x"
docker=29.x.x
```

`java`가 1.8로 나오면 새 터미널을 열거나 아래를 실행한다.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && export PATH="$JAVA_HOME/bin:$PATH"
```

Docker 데몬이 죽어 있으면 Docker Desktop을 먼저 켠다.

---

## 1. 데이터베이스 켜기

### 무엇을 하나

PostgreSQL 컨테이너를 띄운다. 이 프로젝트는 테스트도 실제 DB에 붙기 때문에 이게 없으면 아무것도 안 된다.

`make up`이 아니라 `postgres`만 지정하는 것에 주의한다. `make up`은 앱 컨테이너까지 띄워서
이미지 빌드에 수 분이 걸리고 8080 포트를 점유해 프론트와 부딪힌다.

### 이거 하셈

```bash
cd ~/Project/course/course-be && docker compose up -d postgres
```

### 이렇게 나오면 정상

`course-postgres` 가 `Up ... (healthy)` 이고 포트가 `0.0.0.0:5432->5432/tcp` 다.

---

## 2. 빌드하고 자동 테스트 돌리기

### 무엇을 하나

컴파일이 되는지, 그리고 직접 코스 생성 로직 11개 시나리오가 통과하는지 한 번에 본다.
직접 손으로 API를 두드리기 전에 여기가 먼저 초록불이어야 한다.

### 이거 하셈

```bash
cd ~/Project/course/course-be && ./gradlew build
```

### 이렇게 나오면 정상

```
BUILD SUCCESSFUL
```

실패하면 아래로 어떤 시나리오가 깨졌는지 본다.

```bash
open build/reports/tests/test/index.html
```

### 직접 코스 생성 테스트만 따로 보고 싶으면

```bash
./gradlew test --tests "*CourseCreationSimulationTest*" -i 2>&1 | grep -E "PASSED|FAILED"
```

11개가 모두 `PASSED` 여야 한다. 각 테스트 이름이 곧 검증하는 규칙이다.

| 테스트 | 확인하는 규칙 |
|---|---|
| `ordersCandidatesByDistanceAndDropsOnesOutOfRange` | 가까운 순 정렬, 3km 밖 제외 |
| `excludesAnchorAndAlreadySelectedPlaces` | 앵커와 이미 고른 장소는 후보에서 빠짐 |
| `prefersTagMatchOverPureDistance` | 태그가 맞으면 멀어도 앞섬 |
| `measuresDistanceFromThePreviousPlace` | 거리 기준이 앵커가 아니라 직전 장소 |
| `ignoresInactiveAndOtherAreaPlaces` | 폐업·타 지역 장소 제외 |
| `returnsEmptyListWhenNoCandidateExistsForTheRole` | 후보 없으면 빈 배열 (단계 건너뛰기) |
| `savesScheduleInRequestOrderAndGeneratesCourseTags` | 순서 저장 + 코스 태그 자동 생성 |
| `renumbersOrderWhenAStepIsSkipped` | 단계를 건너뛰어도 순서가 1부터 빈틈없이 |
| `allowsACafeScheduleEvenWhenTheAnchorIsACafe` | 앵커가 카페여도 카페 일정 가능 |
| `rejectsDuplicatePlaceWithinACourse` | 같은 장소 중복 거부 |
| `rejectsInactivePlaceInSchedule` | 폐업 장소 저장 거부 |

---

## 3. 테스트용 장소 데이터 넣기

### 무엇을 하나

지금 DB에는 장소가 **0개**다. 외부 API 키가 없어 수집을 못 돌리기 때문이다.
그래서 손으로 확인할 수 있도록 가짜 장소 11개를 넣는다.

이 데이터는 앵커에서의 거리를 의도적으로 벌려놨다.

```
931001 성수 복합문화공간 (앵커, ACTIVITY)
  ├─ 931003 가까운 팝업스토어   ACTIVITY   56m    태그 POPUP
  ├─ 931004 먼 전시공간         ACTIVITY  1112m   태그 EXHIBITION
  ├─ 931005 반경 밖 서점        ACTIVITY  4448m   ← 3km 밖이라 후보에서 빠져야 함
  ├─ 931006 가까운 한식당       MEAL        14m   태그 KOREAN_FOOD
  ├─ 931007 먼 일식당           MEAL       612m   태그 JAPANESE_FOOD
  ├─ 931008 가까운 디저트 카페  CAFE        28m   태그 DESSERT
  ├─ 931009 먼 베이커리         CAFE       167m   태그 BREAD
  ├─ 931010 폐업한 식당         MEAL              ← is_active=false
  └─ 931011 다른 Area 카페      CAFE              ← 다른 지역
931002 성수 앵커 카페 (앵커, CAFE)   ← "앵커가 카페인 경우" 확인용
```

### 이거 하셈

```bash
make course-seed
```

### 이렇게 나오면 정상

`테스트 장소 11개를 넣었습니다.`가 나오면 된다. **몇 번을 다시 실행해도 안전하다** (넣기 전에 먼저 지운다).

실제로 뭐가 들어갔는지 표로 보려면 `make course-show`를 실행한다. 장소마다 유형·영업 여부·태그가 나온다.

---

## 4. 서버 띄우기

### 무엇을 하나

빌드된 앱을 실행한다. 8080은 프론트(course-fe)가 쓰고 있을 수 있어 8081을 쓴다.

### 이거 하셈

```bash
make run
```

### 이렇게 나오면 정상

```
Started CourseBackendApplication in 2.5 seconds
```

진행률이 `80% EXECUTING`에서 멈춰 있는 게 **정상**이다 (서버가 살아 있다는 뜻). 이 터미널은 그대로 두고,
**새 터미널(`Cmd+T`)을 열어** 다음 단계로 간다. 서버를 멈출 땐 이 터미널에서 `Ctrl+C`.

> **5~13번을 한 번에 하려면** 새 터미널에서 `make course-check`를 실행한다. `http/course-creation.http`의 요청 16개를
> 순서대로 실행하고, 기대한 상태 코드와 같으면 ✅, 다르면 ❌로 보여준다. 아래 단계별 curl은 하나씩 자세히 볼 때용이다.

포트가 이미 쓰인다고 하면 누가 쓰는지 확인한다.

```bash
lsof -nP -iTCP:8081 -sTCP:LISTEN
```

---

## 4-1. 코스 초안 한 번에 받기

### 무엇을 하나

사용자가 앵커와 태그를 정하면 서버가 **칸마다 추천순 목록**을 한 번에 돌려준다. 각 목록의 0번째가 추천 장소이고,
다음 칸은 **앞 칸의 추천 장소에서** 거리를 잰다. 아래 5~9번은 이 중 한 칸을 다시 받거나 바꿀 때 쓰는 API다.

### 이거 하셈

```bash
curl -s "localhost:8081/api/v1/courses/draft?anchorPlaceId=931001&tagCodes=EXHIBITION,BREAD" | jq -r '.slots[] | "\(.order). \(.role)  기준=\(.basePlaceId)  → " + ([.places[] | "\(.name) \(.score)"] | join(" / "))'
```

### 이렇게 나오면 정상

```
1. ACTIVITY  기준=931001  → 먼 전시공간 73.64 / 가까운 팝업스토어 39.81
2. MEAL  기준=931004  → 먼 일식당 28.75 / 가까운 한식당 13.82
3. CAFE  기준=931007  → 먼 베이커리 90.56 / 성수 앵커 카페 55.25 / 가까운 디저트 카페 25.92
```

- 1칸은 `전시` 태그가 맞는 전시공간이 1등이다
- 2칸은 기준이 1칸 추천인 `931004`로 바뀌었고, `전시`·`빵` 태그를 가진 식당이 없어서 거리로만 정렬됐다
- 3칸은 `전시` 태그를 가진 카페가 없어서 `빵`으로만 채점했고, 그래서 베이커리가 태그 만점을 받았다

칸 구성을 바꾸려면 `roles`를 준다. 예) 놀곳·식당·카페·식당

```bash
curl -s "localhost:8081/api/v1/courses/draft?anchorPlaceId=931001&roles=ACTIVITY,MEAL,CAFE,MEAL" | jq -r '.slots[] | "\(.order). \(.role) → \(.places[0].name // "(후보 없음)")"'
```

4칸 식당에는 2칸에서 추천한 한식당이 빠져서 일식당이 나온다.

---

## 5. 1단계 — 활동 장소 후보 받기

### 무엇을 하나

앵커(성수 복합문화공간)를 기준으로 첫 일정인 **활동** 후보를 받는다.
태그를 안 주면 가까운 순으로 나온다.

### 이거 하셈

```bash
curl -s "http://localhost:8081/api/v1/courses/candidates?anchorPlaceId=931001&role=ACTIVITY" | python3 -m json.tool
```

### 이렇게 나오면 정상

- `931003 가까운 팝업스토어` 가 먼저, `931004 먼 전시공간` 이 나중
- `931005 반경 밖 서점`은 **안 나온다** (3km 밖)
- `distanceMeters`가 각각 `56`, `1112`
- `score`는 `39.81`, `13.64` (거리 점수만 반영됨). `tagScore`는 둘 다 `0.0`

보기 좋게 요약해서 보고 싶으면:

```bash
curl -s "http://localhost:8081/api/v1/courses/candidates?anchorPlaceId=931001&role=ACTIVITY" | python3 -c "
import json,sys
for c in json.load(sys.stdin): print(f\"{c['placeId']} {c['name']:<14} {c['distanceMeters']:>5}m  점수 {c['score']:>6}  태그 {c['matchedTags']}\")"
```

---

## 6. 태그를 주면 순위가 뒤집히는지 보기

### 무엇을 하나

`EXHIBITION`(전시) 태그를 주면, 20배 더 먼 장소가 1등으로 올라오는지 본다.
태그 60점 · 거리 40점 배점이 실제로 동작하는지 확인하는 단계다.

### 이거 하셈

```bash
curl -s "http://localhost:8081/api/v1/courses/candidates?anchorPlaceId=931001&role=ACTIVITY&tagCodes=EXHIBITION" | python3 -c "
import json,sys
for c in json.load(sys.stdin): print(f\"{c['placeId']} {c['name']:<14} {c['distanceMeters']:>5}m  점수 {c['score']:>6}  태그 {c['matchedTags']}\")"
```

### 이렇게 나오면 정상

```
931004 먼 전시공간        1112m  점수  73.64  태그 ['EXHIBITION']
931003 가까운 팝업스토어     56m  점수  39.81  태그 []
```

거리로는 20배 가까운 팝업스토어가 태그 때문에 밀려났다.

---

## 7. 2단계 — 직전 장소를 기준으로 식사 후보 받기

### 무엇을 하나

기획서의 핵심 규칙을 확인한다. **다음 후보는 앵커가 아니라 직전에 고른 장소를 기준으로 찾는다.**
`fromPlaceId`를 주는 것과 안 주는 것의 결과가 뒤집혀야 한다.

### 이거 하셈

```bash
echo "── 앵커 기준 ──"
curl -s "http://localhost:8081/api/v1/courses/candidates?anchorPlaceId=931001&role=MEAL" | python3 -c "
import json,sys
for c in json.load(sys.stdin): print(f\"  {c['name']:<12} {c['distanceMeters']:>5}m\")"

echo "── 먼 전시공간(931004)을 고른 뒤 ──"
curl -s "http://localhost:8081/api/v1/courses/candidates?anchorPlaceId=931001&role=MEAL&fromPlaceId=931004" | python3 -c "
import json,sys
for c in json.load(sys.stdin): print(f\"  {c['name']:<12} {c['distanceMeters']:>5}m\")"
```

### 이렇게 나오면 정상

```
── 앵커 기준 ──
  가까운 한식당        14m
  먼 일식당          612m
── 먼 전시공간(931004)을 고른 뒤 ──
  먼 일식당          500m
  가까운 한식당     1101m
```

순서가 뒤집혔다면 직전 장소 기준으로 잘 재계산된 것이다.

---

## 8. 이미 고른 장소가 다시 안 나오는지 보기

### 무엇을 하나

중복 방지를 확인한다. 앵커는 서버가 알아서 빼주고, **이미 고른 장소는 `excludePlaceIds`로 알려줘야 한다.**

### 이거 하셈

```bash
curl -s "http://localhost:8081/api/v1/courses/candidates?anchorPlaceId=931001&role=ACTIVITY&excludePlaceIds=931003" | python3 -c "
import json,sys
print([c['placeId'] for c in json.load(sys.stdin)])"
```

### 이렇게 나오면 정상

```
[931004]
```

`931003`이 사라졌다.

---

## 9. 후보가 없을 때 어떻게 되는지 보기

### 무엇을 하나

"그 단계에 넣을 장소가 하나도 없으면?" 을 확인한다.
에러가 아니라 **빈 배열**이 와야 화면에서 그 단계를 건너뛸 수 있다.

### 이거 하셈

```bash
curl -s "http://localhost:8081/api/v1/courses/candidates?anchorPlaceId=931001&role=CAFE&excludePlaceIds=931002,931008,931009" -w "\nHTTP %{http_code}\n"
```

### 이렇게 나오면 정상

```
[]
HTTP 200
```

`404`나 `500`이 아니라 `200`에 빈 배열이다.

---

## 10. 코스 저장하기

### 무엇을 하나

지금까지 고른 앵커와 일정 3개를 코스로 저장한다.
**저장 순서가 그대로 방문 순서**가 되고, 장소 태그에서 **코스 태그가 자동 생성**된다.

### 이거 하셈

```bash
curl -s -X POST http://localhost:8081/api/v1/courses \
  -H "Content-Type: application/json" \
  -w "\nHTTP %{http_code}\n" \
  -d '{
    "anchorPlaceId": 931001,
    "title": "성수 하루 코스",
    "description": "앵커에서 시작하는 기본 코스",
    "scheduleItems": [
      {"placeId": 931003, "role": "ACTIVITY"},
      {"placeId": 931006, "role": "MEAL"},
      {"placeId": 931008, "role": "CAFE"}
    ]
  }' | python3 -m json.tool
```

### 이렇게 나오면 정상

- `HTTP 201`
- `"published": false` ← 직접 만든 코스는 **비공개로 시작**한다
- `scheduleItems`의 `itemOrder`가 `1, 2, 3`
- `generatedTags`에 `["DESSERT", "KOREAN_FOOD", "POPUP"]` 같은 값이 들어있다
  (장소 태그에서 자동 계산된 것. 비어 있으면 대표 코스 추천에 잡히지 않는다)

### DB에 실제로 들어갔는지 직접 보기

```bash
make course-show
```

맨 아래 `■ 만들어진 코스` 표에 방금 저장한 코스가 일정 순서·공개 여부·자동 생성된 코스 태그와 함께 나온다.

---

## 11. 단계를 건너뛴 코스 저장하기

### 무엇을 하나

9번에서 카페 후보가 0개였다면 어떻게 저장되는지 확인한다.
**빈 순서가 생기지 않고 1, 2로 다시 매겨져야 한다.**

### 이거 하셈

```bash
curl -s -X POST http://localhost:8081/api/v1/courses \
  -H "Content-Type: application/json" \
  -d '{
    "anchorPlaceId": 931001,
    "title": "카페를 건너뛴 코스",
    "scheduleItems": [
      {"placeId": 931004, "role": "ACTIVITY"},
      {"placeId": 931007, "role": "MEAL"}
    ]
  }' | python3 -c "
import json,sys
d=json.load(sys.stdin)
print([(i['itemOrder'], i['placeName']) for i in d['scheduleItems']])"
```

### 이렇게 나오면 정상

```
[(1, '먼 전시공간'), (2, '먼 일식당')]
```

`3`번 자리가 비어 있는 게 아니라 애초에 2개까지만 있다.

---

## 12. 앵커가 카페인 경우

### 무엇을 하나

"사용자가 첫 앵커로 카페를 고를 수 있다"를 확인한다.
앵커가 카페여도 마지막 카페 일정을 그대로 담을 수 있고, **다른 카페면 허용**된다.

### 이거 하셈

```bash
curl -s -X POST http://localhost:8081/api/v1/courses \
  -H "Content-Type: application/json" -w "\nHTTP %{http_code}\n" \
  -d '{
    "anchorPlaceId": 931002,
    "title": "카페에서 시작하는 코스",
    "scheduleItems": [
      {"placeId": 931003, "role": "ACTIVITY"},
      {"placeId": 931006, "role": "MEAL"},
      {"placeId": 931008, "role": "CAFE"}
    ]
  }' | head -c 200
```

### 이렇게 나오면 정상

`HTTP 201`. 앵커가 카페(`931002`)여도 마지막 카페(`931008`)가 들어간다.
단 **같은 카페를 두 번** 넣으면 막힌다 — 아래에서 확인한다.

---

## 13. 막혀야 하는 것들 한 번에 확인

### 무엇을 하나

잘못된 요청이 제대로 거절되는지 몰아서 본다. 여기서 `500`이 하나라도 나오면 버그다.

### 이거 하셈

```bash
BASE=http://localhost:8081/api/v1/courses
p() { printf "%-28s → HTTP %s %s\n" "$1" \
  "$(curl -s -o /tmp/r.json -w '%{http_code}' -X POST "$BASE" -H 'Content-Type: application/json' -d "$2")" \
  "$(head -c 60 /tmp/r.json)"; }

p "앵커를 일정에 또 넣기"   '{"anchorPlaceId":931001,"title":"a","scheduleItems":[{"placeId":931001,"role":"ACTIVITY"}]}'
p "같은 장소 두 번"        '{"anchorPlaceId":931001,"title":"a","scheduleItems":[{"placeId":931003,"role":"ACTIVITY"},{"placeId":931003,"role":"MEAL"}]}'
p "폐업한 장소"            '{"anchorPlaceId":931001,"title":"a","scheduleItems":[{"placeId":931010,"role":"MEAL"}]}'
p "없는 장소"              '{"anchorPlaceId":931001,"title":"a","scheduleItems":[{"placeId":999999,"role":"ACTIVITY"}]}'
p "없는 앵커"              '{"anchorPlaceId":999999,"title":"a","scheduleItems":[{"placeId":931003,"role":"ACTIVITY"}]}'
p "제목 없음"              '{"anchorPlaceId":931001,"scheduleItems":[{"placeId":931003,"role":"ACTIVITY"}]}'
p "일정 0개"               '{"anchorPlaceId":931001,"title":"a","scheduleItems":[]}'
p "role 오타"              '{"anchorPlaceId":931001,"title":"a","scheduleItems":[{"placeId":931003,"role":"XX"}]}'

echo "── 후보 조회 쪽 ──"
q() { printf "%-28s → HTTP %s %s\n" "$1" \
  "$(curl -s -o /tmp/r.json -w '%{http_code}' "$2")" "$(head -c 60 /tmp/r.json)"; }
q "role 오타"        "$BASE/candidates?anchorPlaceId=931001&role=ACTIVIT"
q "role 누락"        "$BASE/candidates?anchorPlaceId=931001"
q "앵커 누락"        "$BASE/candidates?role=ACTIVITY"
q "앵커가 숫자 아님" "$BASE/candidates?anchorPlaceId=abc&role=ACTIVITY"
q "없는 앵커"        "$BASE/candidates?anchorPlaceId=999999&role=ACTIVITY"
```

### 이렇게 나오면 정상

```
앵커를 일정에 또 넣기        → HTTP 400 COURSE_DUPLICATE_PLACE
같은 장소 두 번              → HTTP 400 COURSE_DUPLICATE_PLACE
폐업한 장소                  → HTTP 404 COURSE_PLACE_NOT_FOUND
없는 장소                    → HTTP 404 COURSE_PLACE_NOT_FOUND
없는 앵커                    → HTTP 404 COURSE_ANCHOR_NOT_FOUND
제목 없음                    → HTTP 400 INVALID_INPUT
일정 0개                     → HTTP 400 INVALID_INPUT
role 오타                    → HTTP 400 INVALID_INPUT
── 후보 조회 쪽 ──
role 오타 / role 누락 / 앵커 누락 / 앵커가 숫자 아님  → 전부 HTTP 400 INVALID_INPUT
없는 앵커                    → HTTP 404 COURSE_ANCHOR_NOT_FOUND
```

**`500`이 하나라도 보이면 그건 버그다.** 그대로 알려주면 된다.

---

## 14. 뒷정리

### 무엇을 하나

넣었던 테스트 장소와 만든 코스를 지운다.
**이걸 안 하면 다음에 `./gradlew build` 할 때 자동 테스트가 깨질 수 있다**
(테스트가 같은 ID를 쓰기 때문이다. 3번 스크립트를 다시 돌려도 정리된다).

### 이거 하셈

```bash
make course-clean
```

### 이렇게 나오면 정상

```bash
docker exec course-postgres psql -U course -d course -c \
  "select count(*) as places from places; select count(*) as courses from courses;"
```

둘 다 `0`이다.

DB를 통째로 초기화하고 싶으면 `make clean` 후 `docker compose up -d postgres`를 한다.
이 경우 Flyway가 처음부터 다시 돌므로 앱을 한 번 띄워야 테이블이 생긴다.

---

## 부록 A. API 요약

### 코스 초안

```
GET /api/v1/courses/draft
```

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `anchorPlaceId` | O | 앵커 장소 ID |
| `roles` | X | 칸 구성. 쉼표로 구분, 최대 10칸. 기본 `ACTIVITY,MEAL,CAFE` |
| `tagCodes` | X | 사용자가 고른 태그. 쉼표로 구분 |
| `size` | X | 칸마다 후보 개수. 기본 10, 최대 30 |

- 칸마다 **추천순으로 정렬된 목록**을 준다. **0번째가 추천**이다
- 첫 칸은 앵커에서, 다음 칸부터는 **앞 칸의 0번째 장소**에서 거리를 잰다 (`basePlaceId`)
- 앞 칸에서 추천한 장소는 뒤 칸 후보에서 빠진다
- 후보가 없는 칸은 빈 목록이고, 다음 칸은 마지막으로 추천된 장소를 계속 기준으로 삼는다
- 사용자가 어떤 칸을 바꾸면 **그 뒤 칸만** 후보 조회 API(`fromPlaceId` = 바꾼 장소)로 다시 받는다

### 후보 조회

```
GET /api/v1/courses/candidates
```

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `anchorPlaceId` | O | 앵커 장소 ID. 이 장소의 지역(Area) 안에서만 후보를 찾는다 |
| `role` | O | `ACTIVITY` / `MEAL` / `CAFE` |
| `fromPlaceId` | X | 직전에 고른 장소. 없으면 앵커 기준 |
| `excludePlaceIds` | X | 이미 고른 장소들. 쉼표로 구분 |
| `tagCodes` | X | 사용자가 고른 태그. 쉼표로 구분 |
| `size` | X | 개수. 기본 10, 최대 30 |

점수는 100점 만점이고 응답의 `scoreBreakdown`에 구성이 그대로 들어온다.

```
태그 적합도 60 + 근접도 40
```

- 태그를 안 주면 태그 점수가 전원 0이라 자동으로 거리 순이 된다
- 검색 반경은 3km이고 그 밖은 아예 제외된다
- **근접도는 800m에서 절반**이 되고 거리의 제곱으로 줄어든다. 0m 40점, 300m 35.1점, 1km 15.6점, 1.5km 8.9점, 3km 2.7점
- **칸 유형에 맞는 태그만 채점한다.** 같은 지역·같은 유형 장소 중 그 태그를 가진 곳이 하나도 없으면 그 칸에서는 뺀다.
  예) `전시, 빵`을 골라도 카페 칸은 `빵`으로만 채점한다
- **태그가 아직 없는 장소는 태그 점수의 절반(30점)을 받는다.** 수집 직후 AI 태깅을 기다리는 장소가
  영구히 밀리는 것을 막기 위한 중립 점수다. 이때 `matchedTags`와 `unmatchedTags`가 모두 비어 있다

태그 점수는 세 가지로 갈린다.

| 장소 상태 | 태그 점수 | 응답 표시 |
|---|---|---|
| 요청 태그와 일치 | 최대 60 | `matchedTags`에 표시 |
| 태그가 아직 없음 | 30 (중립) | 양쪽 모두 빈 배열 |
| 태그가 있는데 불일치 | 0 | `unmatchedTags`에 표시 |

### 코스 저장

```
POST /api/v1/courses   → 201
```

```json
{
  "anchorPlaceId": 931001,
  "title": "성수 하루 코스",
  "description": "선택",
  "scheduleItems": [
    {"placeId": 931003, "role": "ACTIVITY"}
  ]
}
```

- `scheduleItems`의 **배열 순서가 곧 방문 순서**다. 서버가 1부터 다시 매긴다
- 코스의 지역은 앵커의 지역을 따라간다
- 저장 직후 코스 태그를 자동 계산한다

---

## 부록 B. 지금 없는 기능 (테스트해도 안 나온다)

아래는 아직 만들지 않은 것들이다. 없다고 버그로 보고할 필요는 없다.

| 없는 것 | 영향 |
|---|---|
| `GET /api/v1/courses/{id}` | 저장한 코스를 다시 조회할 방법이 없다 |
| 코스 공개 전환 API | `is_published`가 계속 `false`라 **대표 코스 추천에 절대 안 나온다** |
| 코스 수정 · 삭제 API | 저장 후 고칠 수 없다 |
| 앵커 목록 조회 API | 앵커를 "둘러보기"로 찾을 수 없다. 키워드 검색만 가능하다 |

또한 아래는 **버그가 아니라 의도한 동작**이다.

- 앵커와 다른 지역의 장소도 일정에 담을 수 있다 (행정 경계가 도보권을 가르기 때문)
- 카페를 `MEAL` 역할로 담을 수 있다 (브런치 카페처럼 유형을 바꿔 쓰는 경우를 위해)
- 앵커가 카페여도 카페 일정을 또 담을 수 있다 (같은 카페만 아니면 된다)

---

## 부록 C. 자주 막히는 곳

| 증상 | 원인과 해결 |
|---|---|
| `Gradle requires JVM 17 or later` | Java 8을 쓰고 있다. 0번 단계 참고 |
| `Connection to localhost:5432 refused` | DB가 꺼져 있다. `docker compose up -d postgres` |
| `Cannot connect to the Docker daemon` | Docker Desktop이 꺼져 있다 |
| `Port 8080 was already in use` | 프론트가 쓰는 중이다. `PORT=8081`로 띄운다 |
| 자동 테스트가 갑자기 다 깨진다 | 수동 테스트 데이터가 남아 있다. 14번으로 정리 |
| 후보가 계속 빈 배열이다 | 3번 시드를 안 넣었거나 14번으로 지운 뒤다 |
| `generatedTags`가 비어 있다 | 담은 장소에 `place_tags`가 없다. 시드로 넣은 장소만 태그가 있다 |
