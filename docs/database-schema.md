# Course 데이터베이스 설계 문서

> **Last updated:** 2026-08-28
> **Migration source of truth:** `src/main/resources/db/migration/` (현재 V1~V15)
> **Database:** PostgreSQL
> **Document rule:** 테이블·컬럼·제약조건·인덱스를 변경할 때는 같은 작업에서 Flyway와 이 문서를 함께 갱신한다.

---

## 📚 목차

1. [문서 개요 및 표기 약속](#1-문서-개요-및-표기-약속)
2. [전체 도메인 구조](#2-전체-도메인-구조)
3. [위치 도메인](#3-위치-도메인)
4. [태그 도메인](#4-태그-도메인)
5. [장소 도메인](#5-장소-도메인)
6. [코스 도메인](#6-코스-도메인)
7. [인덱스 및 수집·동기화 정책](#7-인덱스-및-수집동기화-정책)
8. [Flyway 변경 이력](#8-flyway-변경-이력)

---

## 1. 문서 개요 및 표기 약속

이 문서는 나들이 코스 추천 서비스의 PostgreSQL 데이터베이스 구조를 정리한다. 실제 스키마 변경의 기준은 Flyway 마이그레이션이다.

| 태그 | 의미 |
|------|------|
| 🔑 PK | Primary Key |
| 🔗 FK | Foreign Key |
| 🟣 UQ | Unique |
| 📍 IDX | Index |
| ✅ CHECK | 값 범위 제약 |

현재 시간 컬럼은 PostgreSQL `TIMESTAMP`, Java `LocalDateTime`을 사용한다.

---

## 2. 전체 도메인 구조

```text
cities 1:N areas 1:N places
             │           │
             │ N:M       │ N:M
             ▼           ▼
          area_tags   place_tags
                \       /
                 ▼     ▼
                   tags
                     ▲
                     │ N:M
                  course_tags

areas 1:N courses 1:N course_items N:1 places
                  courses N:1 anchor_place(places)
```

1. City 입력 후 사용자 키워드와 `area_tags`를 점수화해 Area를 추천한다.
2. Area 선택 후 사용자 키워드와 `place_tags`를 점수화해 앵커 후보와 일정 장소를 추천한다.
3. 특정 Place를 직접 입력하면, 앵커 후보 여부와 관계없이 이번 코스의 앵커가 된다.

---

## 3. 위치 도메인

### 📋 cities

도시 단위의 상위 위치다. 예: 서울, 대전.

| 컬럼 | 타입 | 태그 | 설명 |
|------|------|------|------|
| `id` | BIGINT | 🔑 PK | 도시 ID, identity |
| `code` | VARCHAR(50) | 🟣 UQ | 변경하지 않는 내부 도시 코드 |
| `name` | VARCHAR(100) | | 화면 표시명 |

### 📋 areas

도시 안에서 장소와 앵커 후보를 탐색하는 세부 지역이다. 예: 성수, 서촌, 용산.

| 컬럼 | 타입 | 태그 | 설명 |
|------|------|------|------|
| `id` | BIGINT | 🔑 PK | 세부 지역 ID, identity |
| `city_id` | BIGINT | 🔗 FK | `cities.id` |
| `code` | VARCHAR(50) | 🟣 UQ (도시 내) | 도시 안에서 유일한 내부 코드 |
| `name` | VARCHAR(100) | | 화면 표시명 |
| `collection_center_latitude` / `collection_center_longitude` | NUMERIC(10,7) | | 초기 장소 수집 범위의 중심 좌표 |
| `collection_min_latitude` / `collection_min_longitude` | NUMERIC(10,7) | | 초기 Kakao `rect` 수집 범위의 남서쪽 좌표 |
| `collection_max_latitude` / `collection_max_longitude` | NUMERIC(10,7) | | 초기 Kakao `rect` 수집 범위의 북동쪽 좌표 |

**제약조건 및 인덱스**

- `uk_areas_city_code (city_id, code)` — 같은 도시 안에서 세부 지역 코드 중복 방지
- 📍 `idx_areas_city_id (city_id)` — 도시별 세부 지역 목록 조회
- 수집 좌표는 해당 Area에 매핑된 법정동 경계를 모두 감싸도록 계산한다. 검색 시작 범위일 뿐, Place의 Area 소속은 카카오 주소의 법정동 매핑으로 결정한다.

### 📋 place_collection_profiles

카카오 카테고리·키워드 검색 조건을 코드 배포 없이 운영 데이터로 관리한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `provider`, `code` | VARCHAR | 제공자 내 수집 프로필 식별자 |
| `search_type` | VARCHAR | `CATEGORY` 또는 `KEYWORD` |
| `category_group_code` / `query` | VARCHAR | 검색 방식에 따른 카카오 조건 |
| `place_type` | VARCHAR | 분류 규칙이 없을 때 사용하는 기본 PlaceType |
| `is_active` | BOOLEAN | 수집 실행 여부 |

`CATEGORY`는 카테고리 코드만, `KEYWORD`는 검색어만 가질 수 있다. 초기 수집은 카테고리 프로필로 기본 장소를 확보하고, 방탈출·보드게임처럼 카테고리 검색에서 누락될 수 있는 장소는 키워드 프로필로 보완한다.

### 📋 place_category_rules

카카오 원본 `category_name` 또는 장소명에 포함된 키워드로 기본 PlaceType을 보정하는 운영 규칙이다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `keyword` | VARCHAR(100) | 카테고리명·장소명에서 찾을 키워드. unique |
| `target_place_type` | VARCHAR(20) | 매칭 시 적용할 `ACTIVITY`, `MEAL`, `CAFE` |
| `priority` | INTEGER | 여러 규칙이 매칭될 때 높은 값 우선 |
| `is_active` | BOOLEAN | 분류 규칙 적용 여부 |

예를 들어 카페 프로필로 수집된 `가정,생활 > 여가시설 > 방탈출카페`는 `방탈출카페 → ACTIVITY` 규칙으로 재분류한다.

### 📋 place_collection_jobs

Area·수집 프로필·rect 단위의 중단·분할 가능한 카카오 수집 작업이다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `parent_job_id` | BIGINT | rect 분할 전 부모 Job. 초기 Job은 null |
| `area_id`, `collection_profile_id` | BIGINT | 수집 대상 Area와 원본 프로필 |
| `profile_code` ~ `place_type` | VARCHAR | Job 생성 시점의 프로필 스냅샷 |
| `min_latitude` ~ `max_longitude` | NUMERIC(10,7) | 현재 Job의 Kakao `rect` 범위 |
| `depth` | INTEGER | rect 분할 깊이 |
| `status` | VARCHAR | `READY`, `RUNNING`, `COMPLETED`, `PARTIAL`, `SPLIT`, `FAILED` |
| `error_message` | VARCHAR | API 실패·최소 rect 포화 원인 |
| `total_count` / `pageable_count` | INTEGER | 현재 rect에 대한 카카오 검색 결과 수와 페이지 조회 가능 수 |

포화된 rect가 500m를 초과하면 4등분하고, 500m 이하부터는 긴 축을 기준으로 2등분한다. 최소 50m에서도 포화되면 첫 페이지 결과를 Temp에 저장하고 `PARTIAL`로 남긴다.

### 📋 place_collection_temp

카카오 원본 장소를 네이버로 1차 정제하고 AI 태깅에 넘기기 전까지 보관하는 임시 테이블이다. 최종 `places`에는 아직 저장하지 않는다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `area_id` | BIGINT | 법정동 주소로 결정한 서비스 Area |
| `provider`, `provider_place_id` | VARCHAR | 카카오 장소 중복 식별자 |
| `default_place_type` | VARCHAR | 수집 프로필과 분류 규칙으로 정한 기본 타입 |
| `name` ~ `phone` | VARCHAR / NUMERIC | 카카오 원본 장소 정보 |
| `naver_title` ~ `naver_road_address_name` | VARCHAR | 주소 매칭으로 선택한 네이버 장소 정보 또는 이전·다른 지점 의심 후보 정보 |
| `naver_search_url` | VARCHAR(1000) | 장소명에 지번주소의 동네명을 보완한 사용자용 네이버 지도 검색 링크 |
| `processing_step` | VARCHAR | `NAVER_ENRICHMENT`, `AI_TAGGING` |
| `status` | VARCHAR | `PENDING`, `PROCESSING`, `FAILED`, `COMPLETED` |
| `attempt_count`, `last_attempt_at` | INTEGER / TIMESTAMP | 정제 시도 횟수와 마지막 시각 |
| `error_code`, `error_message` | VARCHAR | 현재 단계의 실패 원인. `NAVER_POSSIBLE_RELOCATION`은 이름·카테고리가 유사하지만 주소가 다른 후보 |

카카오 수집 직후에는 `NAVER_ENRICHMENT / PENDING`이다. 네이버 후보 중 지번주소 또는 도로명주소가 같은 장소를 선택하면 네이버 정보를 저장하고 `AI_TAGGING / PENDING`으로 전환한다. AI 태깅과 최종 `Place·PlaceTag` 저장이 끝나면 `AI_TAGGING / COMPLETED`가 된다.

---

## 4. 태그 도메인

### 📋 tags

사용자 입력, Area, Place, Course를 같은 기준으로 연결하는 공통 태그 사전이다. 대표 태그와 유사 화면 선택지는 DB와 CSV seed에서 관리한다.

| 컬럼 | 타입 | 태그 | 설명 |
|------|------|------|------|
| `id` | BIGINT | 🔑 PK | 태그 ID, identity |
| `code` | VARCHAR(50) | 🟣 UQ | 추천 매칭에 사용하는 대표 태그 코드 |
| `display_name` | VARCHAR(100) | | 대표 태그 표시명 |
| `is_active` | BOOLEAN | | 선택지 노출 여부 |
| `display_order` | INTEGER | | 화면 노출 순서 |

### 📋 tag_options

대표 태그에 속한 유사 선택지다. 예를 들어 `EXHIBITION` 태그는 `전시 관람`, `갤러리`, `미술관·박물관`을 가질 수 있다. 사용자가 어떤 선택지를 골라도 추천 요청에는 대표 태그 코드 하나만 전달한다.

| 컬럼 | 타입 | 태그 | 설명 |
|------|------|------|------|
| `id` | BIGINT | 🔑 PK | 선택지 ID, identity |
| `tag_id` | BIGINT | 🔗 FK | 대표 태그. `tags.id` |
| `option_name` | VARCHAR(100) | 🟣 UQ (태그 내) | 화면에 표시할 유사 선택지 |
| `display_order` | INTEGER | | 태그 내 노출 순서 |
| `is_active` | BOOLEAN | | 선택지 노출 여부 |

### 📋 area_tags

세부 지역이 어떤 활동·음식·분위기를 대표하는지 나타내는 Area와 Tag의 연결 테이블이다.

| 컬럼 | 타입 | 태그 | 설명 |
|------|------|------|------|
| `id` | BIGINT | 🔑 PK | 연결 ID, identity |
| `area_id` | BIGINT | 🔗 FK | `areas.id` |
| `tag_id` | BIGINT | 🔗 FK | `tags.id` |
| `weight` | INTEGER | ✅ CHECK | 해당 Area에서 태그의 대표성. 0~100 |

### 📋 place_tags

장소가 제공하는 활동·음식·분위기 특성을 나타내는 Place와 Tag의 연결 테이블이다.

| 컬럼 | 타입 | 태그 | 설명 |
|------|------|------|------|
| `id` | BIGINT | 🔑 PK | 연결 ID, identity |
| `place_id` | BIGINT | 🔗 FK | `places.id` |
| `tag_id` | BIGINT | 🔗 FK | `tags.id` |
| `weight` | INTEGER | ✅ CHECK | 해당 Place에서 태그의 대표성. 0~100 |

**💡 설계 포인트**

- `tags.csv`는 대표 태그를, `tag_options.csv`는 태그별 유사 선택지를 관리한다.
- 화면은 `tag_options`를 선택지로 노출한다. 어떤 선택지를 고르든 해당 대표 태그 `code` 하나만 요청·저장·점수 계산에 사용한다.
- 사용자가 같은 태그에 속한 선택지를 여러 개 고르더라도, 추천 점수 계산 전에는 대표 태그 기준으로 중복 제거한다.
- `tags`는 `TagCode`를 DB 관계에 사용할 수 있도록 보관하는 공통 사전이며, `area_tags`와 `place_tags`는 같은 태그를 다른 대상에 연결한다.
- `weight`는 태그 보유 여부가 아닌, 태그가 Area 또는 Place를 얼마나 대표하는지 나타낸다.
- 같은 Area 또는 Place에 같은 Tag를 중복 연결할 수 없다.
- Area와 Place는 서로 다른 테이블이므로 연결 테이블을 분리한다. 하나의 `target_type`, `target_id` 테이블로 합치면 FK 무결성을 보장할 수 없다.

---

## 5. 장소 도메인

### 📋 places

외부 제공자에서 수집·정제한 장소 마스터다. 추천은 외부 API 응답을 즉시 사용하지 않고, 이 테이블의 활성 장소를 대상으로 수행한다.

| 컬럼 | 타입 | 태그 | 설명 |
|------|------|------|------|
| `id` | BIGINT | 🔑 PK | 장소 ID, identity |
| `provider` | VARCHAR(30) | ✅ CHECK | 외부 제공자: `KAKAO`, `TOUR` |
| `provider_place_id` | VARCHAR(100) | 🟣 UQ (제공자 내) | 외부 제공자가 발급한 장소 ID |
| `area_id` | BIGINT | 🔗 FK | 소속 세부 지역. `areas.id` |
| `place_type` | VARCHAR(20) | ✅ CHECK | 장소의 기본 유형: `ACTIVITY`, `MEAL`, `CAFE` |
| `name` | VARCHAR(200) | | 장소명 |
| `address_name` | VARCHAR(500) | | 지번 주소. nullable |
| `road_address_name` | VARCHAR(500) | | 도로명 주소. nullable |
| `source_category_name` | VARCHAR(500) | | 카카오 원본 상세 카테고리. nullable |
| `source_category_group_code` | VARCHAR(20) | | 카카오 원본 주요 카테고리 코드. nullable 또는 빈 값 가능 |
| `latitude` / `longitude` | NUMERIC(10,7) | | 위도 / 경도 |
| `place_url` | VARCHAR(1000) | | 카카오 등 수집 제공자의 원본 상세 URL. nullable |
| `naver_search_url` | VARCHAR(1000) | | 정제 과정에서 생성한 네이버 지도 검색 링크. nullable |
| `phone` | VARCHAR(50) | | 전화번호. nullable |
| `is_anchor_candidate` | BOOLEAN | | 앵커 후보 노출 여부. 기본 `false` |
| `is_active` | BOOLEAN | | 추천·수집 대상 활성 여부. 기본 `true` |
| `priority_score` | INTEGER | ✅ CHECK | 운영상 추가 노출에 사용하는 우선도 점수. 기본 `0` |
| `operating_hours` / `operating_days` | VARCHAR | | 운영 정보 원문. nullable |
| `last_synced_at` | TIMESTAMP | | 외부 정보 마지막 동기화 시각 |
| `created_at` / `updated_at` | TIMESTAMP | | 최초 생성 / DB 레코드 마지막 수정 시각 |

**제약조건**

- `uk_places_provider_place_id (provider, provider_place_id)` — 같은 제공자의 같은 장소 중복 수집 방지
- `ck_places_provider` — `KAKAO`, `TOUR`만 허용
- `ck_places_place_type` — `ACTIVITY`, `MEAL`, `CAFE`만 허용
- `ck_places_priority_score_non_negative` — 우선도 점수는 0 이상
- 장소 행동 집계는 `place_stats`에서 관리한다.

**💡 설계 포인트**

- 카카오 원본 카테고리는 보존하고, `place_category_rules`가 매칭되면 `place_type`을 보정한다. 규칙이 없으면 수집 프로필 기본 타입을 사용한다.
- `is_anchor_candidate=false`인 Place라도 사용자가 직접 선택하면 이번 코스의 앵커가 될 수 있다.
- `updated_at`은 모든 DB 수정 시 갱신되고, `last_synced_at`은 외부 정보 최신화 배치의 기준이다.

---

## 6. 코스 도메인

대표 코스는 사용자 입력마다 새로 생성하지 않고, 운영자가 미리 저장하거나 별도 생성 배치가 저장한 코스를 추천 대상으로 사용한다. 코스는 하나의 세부 지역과 필수 앵커 장소를 가지며, 실제 방문 순서는 `course_items`로 관리한다.

### 📋 courses

| 컬럼 | 타입 | 태그 | 설명 |
|------|------|------|------|
| `id` | BIGINT | 🔑 PK | 코스 ID, identity |
| `area_id` | BIGINT | 🔗 FK | 코스가 시작하는 세부 지역. `areas.id` |
| `anchor_place_id` | BIGINT | 🔗 FK | 코스의 기준 앵커. `places.id` |
| `title` | VARCHAR(200) | | 대표 코스명 |
| `description` | VARCHAR(2000) | | 코스 소개. nullable |
| `is_published` | BOOLEAN | | 대표 코스 노출 여부. 기본 `false` |
| `created_at` / `updated_at` | TIMESTAMP | | 생성 / 마지막 수정 시각 |

### 📋 course_items

코스의 일정 장소 목록이다. `item_order`로 방문 순서를 표현한다. `item_role`은 장소의 기본 유형과 별개로 이번 코스에서 사용하는 목적을 나타낸다. 예를 들어 `PlaceType.CAFE`인 빵집도 빵 투어에서는 `CourseItemRole.ACTIVITY`로 방문할 수 있다.

| 컬럼 | 타입 | 태그 | 설명 |
|------|------|------|------|
| `id` | BIGINT | 🔑 PK | 일정 항목 ID, identity |
| `course_id` | BIGINT | 🔗 FK | 소속 코스. `courses.id` |
| `place_id` | BIGINT | 🔗 FK | 방문 장소. `places.id` |
| `item_role` | VARCHAR(20) | ✅ CHECK | 코스 내 역할: `ACTIVITY`, `MEAL`, `CAFE` |
| `item_order` | INTEGER | ✅ CHECK | 코스 내 방문 순서(0 이상) |

### 📋 course_tags

코스 전체의 활동·음식·분위기를 나타내는 공통 `tags` 연결 테이블이다. 수동 입력하지 않고 앵커와 일정 장소의 `PlaceTag`를 중복 장소 없이 집계한다. `최대 PlaceTag 가중치 × 0.4 + 태그 보유 장소 비율 × 60`으로 계산하며 30 미만은 저장하지 않는다.

| 컬럼 | 타입 | 태그 | 설명 |
|------|------|------|------|
| `id` | BIGINT | 🔑 PK | 연결 ID, identity |
| `course_id` | BIGINT | 🔗 FK | `courses.id` |
| `tag_id` | BIGINT | 🔗 FK | `tags.id` |
| `weight` | INTEGER | ✅ CHECK | 코스에서 태그의 대표성. 0~100 |

### 📋 course_companion_types / course_time_slots

코스에 적합한 동행자 유형과 시간대를 연결한다. 연결 정보가 없으면 추천 점수에서 부적합으로 보지 않고 중립값을 적용한다.

| 테이블 | 주요 컬럼 | 설명 |
|------|------|------|
| `course_companion_types` | `course_id`, `companion_type` | `FRIEND`, `LOVER`, `FAMILY` 중 복수 연결 가능 |
| `course_time_slots` | `course_id`, `time_slot` | `MORNING`, `AFTERNOON`, `NIGHT` 중 복수 연결 가능 |

**제약조건 및 설계 포인트**

- `uk_course_items_course_order (course_id, item_order)` — 한 코스에서 순서 중복 방지
- `ck_course_items_item_role` — `ACTIVITY`, `MEAL`, `CAFE`만 허용
- `uk_course_tags_course_tag (course_id, tag_id)` — 같은 태그 중복 연결 방지
- 코스 삭제 시 `course_items`, `course_tags` 연결만 함께 삭제한다. 공유 장소(`places`)는 삭제하지 않는다.
- `courses.area_id`와 `courses.anchor_place_id`의 실제 지역 일치 여부는 코스 생성 서비스에서 검증한다.

### 📋 place_stats / course_stats

장소와 코스에 대한 사용자 행동을 본체와 분리해 누적 집계한다. 현재는 추천 노출·조회·선택·저장·공유 횟수를 관리한다. `impression_count`는 코스 추천 응답에 최종 포함된 횟수이며 `course_stats`에만 존재한다. 정확한 화면 노출이나 기간별 분석이 필요해지면 별도 이벤트 테이블을 추가한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `place_id` / `course_id` | BIGINT | 대상 본체와의 1:1 FK |
| `impression_count` | BIGINT | 추천 결과에 포함된 횟수. `course_stats` 전용 |
| `view_count` | BIGINT | 조회 횟수 |
| `selection_count` | BIGINT | 선택 횟수 |
| `save_count` | BIGINT | 저장 횟수 |
| `share_count` | BIGINT | 공유 횟수 |
| `last_viewed_at` / `last_selected_at` | TIMESTAMP | 조회·선택 마지막 시각 |
| `last_saved_at` / `last_shared_at` | TIMESTAMP | 저장·공유 마지막 시각 |

## 7. 인덱스 및 수집·동기화 정책

| 인덱스 / 제약 | 용도 |
|------|------|
| `uk_places_provider_place_id (provider, provider_place_id)` | 동일 외부 장소 중복 수집 방지 및 upsert 대상 식별 |
| `idx_places_area_anchor_active (area_id, is_anchor_candidate, is_active)` | 세부 지역별 활성 앵커 후보 조회 |
| `idx_places_area_type_active (area_id, place_type, is_active)` | 세부 지역·일정 유형별 활성 장소 후보 조회 |
| `idx_places_last_synced_at (last_synced_at)` | 장기간 동기화되지 않은 장소를 배치로 조회 |
| `idx_area_tags_tag_area (tag_id, area_id)` | 선택한 키워드로 Area 후보를 역방향 조회 |
| `idx_place_tags_tag_place (tag_id, place_id)` | 선택한 키워드로 Place 후보를 역방향 조회 |
| `idx_place_stats_selection_count (selection_count)` | 선택 횟수 기반 장소 통계 조회 |
| `idx_course_stats_selection_count (selection_count)` | 선택 횟수 기반 코스 통계 조회 |
| `idx_place_collection_temp_step_status_id (processing_step, status, id)` | 네이버·AI 단계별 대기 데이터를 오래된 순서로 배치 조회 |

```text
카카오 API 수집
→ place_collection_temp에 원본 저장
→ category_name·장소명에 분류 규칙 적용
→ NAVER_ENRICHMENT / PENDING

네이버 1차 정제
→ 장소명에 지번주소의 동명을 보완해 후보 최대 5개 검색, 일치 후보가 없으면 이름 + 도로명주소(없으면 지번주소)로 재검색
→ 도로명주소를 우선 비교하고 지번주소를 보조 비교한다. 같은 주소·유사 상호 후보가 여러 개면 카카오·네이버 카테고리 공통 항목으로 한 번 더 선택한다.
→ 사용자용 네이버 지도 검색 링크·카테고리·주소 저장
→ AI_TAGGING / PENDING

AI 태깅
→ 활성 Tag 코드만 허용해 OpenAI Structured Outputs 요청
→ PlaceType과 태그 가중치 검증
→ 성공 시 최종 Place·PlaceTag 저장
→ AI_TAGGING / COMPLETED

초기 수집
→ 카테고리 프로필 검색
→ 키워드 프로필 검색
→ 포화 rect 분할

재수집
→ 초기 수집과 분리된 갱신 전략을 별도 설계
```

---

## 8. Flyway 변경 이력

| 버전 | 파일 | 내용 |
|------|------|------|
| V1 | `V1__create_location_and_place_domain.sql` | 도시, 세부 지역, 장소, 공통 태그 및 초기 인덱스 생성 |
| V2 | `V2__create_course_domain.sql` | 대표 코스, 코스 일정 항목, 코스 태그 및 추천용 인덱스 생성 |
| V3 | `V3__separate_course_and_place_stats.sql` | 장소·코스 통계 분리 및 기존 선택 통계 이관 |
| V4 | `V4__create_tag_options.sql` | 태그 표시 정보와 유사 선택지 테이블 생성 |
| V5 | `V5__seed_master_data.java` | CSV 기준 도시·태그·태그 선택지 적재 |
| V6 | `V6__add_course_context_options.sql` | 코스별 동행자 유형과 시간대 적합도 연결 |
| V7 | `V7__add_course_impression_count.sql` | 추천 결과에 포함된 코스의 노출 횟수 추가 |
| V8 | `V8__add_course_item_role.sql` | 장소 유형 `FOOD`를 `MEAL`로 이관하고 코스 일정 역할 추가 |
| V9 | `V9__seed_seoul_areas.java` | 서울 서비스 Area와 초기 Kakao rect 범위 적재 |
| V10 | `V10__create_place_collection.sql` | 카카오 수집 프로필·분할 Job 테이블 생성 |
| V11 | `V11__add_partial_collection_job_status.sql` | 최소 rect 포화 시 일부 저장 상태 추가 |
| V12 | `V12__add_place_category_classification.sql` | 카카오 원본 카테고리, DB 분류 규칙, 키워드 수집 프로필 추가 |
| V13 | `V13__record_collection_search_counts.sql` | Job별 카카오 rect 검색 결과 수 기록 |
| V14 | `V14__add_place_priority_score.sql` | 운영상 장소 노출 우선도 점수 추가 |
| V15 | `V15__create_place_collection_temp.sql` | 카카오 원본과 네이버 1차 정제 결과를 보관하는 Temp 테이블 추가 |
| V16 | `V16__complete_ai_tagging_status.sql` | AI 태깅 완료 상태 추가 |
| V17 | `V17__store_naver_search_url.sql` | 네이버 검색 링크 명확화 및 최종 Place 저장 컬럼 추가 |
| V18 | `V18__clear_legacy_naver_external_urls.sql` | 기존 업체 홈페이지·빈 링크 값을 임시 데이터에서 제거 |
| V19 | `V19__clear_legacy_naver_search_urls.sql` | 기존 네이버 통합검색 링크를 제거하고 네이버 지도 검색 링크로 전환 |
