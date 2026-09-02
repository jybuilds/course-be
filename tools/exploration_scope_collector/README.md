# CourseAnchor Data Pipeline

공식·공공 원문에서 `AREA`/`HUB` 후보를 수집하고, 현재 Spring 백엔드의 추천 설정에 맞는 규칙 후보 JSON을 만드는 오프라인 Python 도구다.

Spring 서버나 PostgreSQL에 직접 연결하지 않는다. 생성물은 항상 검수 후 별도 import해야 한다.

## 확정된 데이터 의미

- `parentRegionName`: 사용자가 처음 선택하는 큰 지역. 서울 작업의 모든 결과는 `서울`이다.
- `collectionRegion`: 서울 자료를 한 번에 조사하지 않기 위한 수집 배치. DB의 지역 데이터가 아니다.
- `AREA`: 성수동·서촌처럼 여러 활동·식사·카페를 묶을 수 있는 동네·거리·관광 권역.
- `HUB`: 코엑스·아이파크몰처럼 특정 복합시설이나 대형 명소 중심점.

```text
서울 입력
  → 8개 수집 배치로 분리
  → 등록된 공식·공공 원문 저장
  → Qwen3 14B로 AREA/HUB 정제
  → 형식·출처·중복 검증
  → 현재 백엔드 활동/선호도 코드로 규칙 평가
  → 검수용 JSON 및 backend-import.json 생성
```

## 실행 환경

- Windows 11
- RX 9070 XT 16GB VRAM
- RAM 24GB
- Python 3.11 이상
- LM Studio + Qwen3 14B GGUF `Q4_K_M`

추가 Python 패키지는 필요하지 않다.

LM Studio 권장 시작 설정:

```text
Context Length: 8192
GPU Offload: 최대
Temperature: 프로그램에서 0.1 고정
Thinking: 프로그램이 /no_think 지시
Local Server: http://localhost:1234
```

## Windows 실행

1. LM Studio에서 Qwen3 14B GGUF `Q4_K_M`을 내려받고 모델을 로드한다.
2. `Developer` 또는 `Local Server` 화면에서 서버를 시작한다.
3. PowerShell에서 백엔드 루트로 이동한다.

```powershell
cd C:\path\to\course_backend
py tools\exploration_scope_collector\collect_scopes.py run --city 서울
```

LM Studio에 모델이 여러 개 보이면 모델 ID를 명시한다.

```powershell
py tools\exploration_scope_collector\collect_scopes.py run `
  --city 서울 `
  --model "LM Studio에 표시되는 정확한 모델 ID"
```

기존 결과를 버리지 않고 이어서 실행하는 것이 기본이다. 원문부터 다시 처리할 때만 `--refresh`를 사용한다.

```powershell
py tools\exploration_scope_collector\collect_scopes.py run --city 서울 --refresh
```

## 단계별 실행

문제가 난 단계를 확인하거나 결과를 중간 검수할 때 사용한다.

```powershell
py tools\exploration_scope_collector\collect_scopes.py init-city --city 서울
py tools\exploration_scope_collector\collect_scopes.py collect --city 서울
py tools\exploration_scope_collector\collect_scopes.py normalize --city 서울
py tools\exploration_scope_collector\collect_scopes.py evaluate --city 서울
```

- `init-city`: 서울 수집 배치 계획 생성
- `collect`: 공식·공공 URL의 원문과 해시 저장
- `normalize`: 소스별 원문에서 AREA/HUB 추출 및 검증
- `evaluate`: 현재 백엔드 활동·선호도 기준으로 규칙 후보 생성
- `run`: 위 단계를 순서대로 실행

## 설정 파일

```text
config/
├─ cities/
│  ├─ index.json
│  └─ seoul.json
├─ source_registry/
│  └─ seoul.json
└─ contracts/
   └─ backend-recommendation.json
```

`cities/seoul.json`의 행정구역 묶음은 수집 작업을 나누기 위한 설정일 뿐 DB에 들어가지 않는다.

`source_registry/seoul.json`에는 프로그램이 접근할 공식·공공 URL만 등록한다. 현재 파일은 서울관광재단 기반의 초기 소스다. 조사 범위를 늘릴 때 자치구·한국관광공사·시설 운영사의 공식 URL을 이 파일에 추가한다.

`backend-recommendation.json`은 현재 `V2__create_recommendation_configuration.sql`의 코드를 복제한 평가 계약이다. 백엔드의 선호도 옵션이나 활동 카테고리를 바꾸면 이 파일도 함께 갱신한다.

## 출력

실행 결과는 Git에 포함되지 않는 `data/seoul` 아래에 생성된다.

```text
data/seoul/
├─ city-manifest.json
├─ raw/                         # 출처 원문·URL·수집 오류
├─ normalized/                  # 배치별 AREA/HUB 후보
├─ evaluation/                  # Anchor별 평가와 두 규칙 목록
├─ catalogue/
│  ├─ anchors-area.json
│  ├─ anchors-hub.json
│  ├─ review-needed.json
│  └─ backend-import.json
└─ run-report.json
```

`backend-import.json`에는 현재 백엔드 필드명과 일치하는 다음 배열이 들어간다.

```text
courseAnchors
courseAnchorActivityRules
courseAnchorPreferenceRules
```

안전상 `courseAnchors[].active`는 항상 `false`로 출력한다. 최종 검수 및 중복 확인 후 활성화해야 한다.

`anchors-area.json`과 `anchors-hub.json`은 검수 상태와 관계없이 발견된 후보를 유형별로 보여 준다. 그중 검수가 필요한 후보는 `review-needed.json`에도 함께 들어가며, `backend-import.json`에는 `ACTIVE_CANDIDATE`만 포함된다.

## 검수 정책

- 입력 원문에 없는 URL을 모델이 만들면 제거한다.
- 좌표가 없거나 대한민국 범위를 벗어나면 `REVIEW_NEEDED`로 이동한다.
- 출처 문구가 없거나 유형이 애매한 후보도 `REVIEW_NEEDED`다.
- 같은 이름은 여러 배치에서 발견돼도 하나로 병합한다.
- 평가기는 허용된 활동·선호도 코드 외의 결과를 제거한다.
- 점수는 현재 시드와 맞춘 `5, 10, 15, 20, 25`만 허용한다.

평가 점수는 운영 확정값이 아니라 현재 기준으로 만든 검수 후보다.

## 테스트

외부 웹과 LM Studio 없이 검증 가능한 테스트만 포함한다.

```powershell
py -m unittest discover tools\exploration_scope_collector\tests -v
```
