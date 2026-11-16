> 2026-11-16 pipeline 정리: 이 문서에 남은 수동 수집 API·즉시 AI·Orchestrator 설명은 이전 구현 기록이다. 해당 API는 제거되었으며 현재 구조와 실행 방법은 [스케줄링 가이드](./scheduling-guide.md)가 기준이다.

# 장소 수집 통합 가이드
2026-11-13 · 구현 및 장애 복구 검증 보고서 · v1.0

## 1. 전체 흐름과 이번 변경
카카오에서 검색한 장소를 Temp에 저장하고, 네이버 검색과 블로그 자료로 보강한 뒤 AI에 분류를 맡긴다. AI 결과는 Place·PlaceTag에 반영하고 Temp를 완료 처리한다. HTTP API는 수동 실행 진입점이며, 복구 스케줄러는 이미 시작한 작업의 중단·실패를 회수한다.

[FLOW]

| 단계 | 입력 | 결과 |
|---|---|---|
| 카카오 수집 | Area 검색 경계, 활성 수집 프로필 | 원본 장소와 좌표를 Temp에 저장 |
| 네이버 정제 | Temp의 상호·지번·도로명주소 | 네이버 매칭 결과, 블로그 요약, AI 대기 상태 |
| AI 태깅 | 원본·정제 자료, 활성 태그, 분류 규칙 | 최종 타입·태그·음식 키워드·추천 점수 |
| 최종 저장 | 검증한 AI 결과 | Place·PlaceTag, 검토 플래그, Temp 완료 기록 |

이번 작업은 오류코드 분류와 재시도 정책을 분리하고, 카카오 작업 계획과 AI 반영 기록을 DB에 남겨 중단 복구가 가능하도록 개선했다. 태깅 프롬프트, 점수 기준, 검토 태그 기준은 변경하지 않았다. 기존 needsReview·OPERATOR 변경도 보존했다.

신규 Area를 언제 몇 개 수집할지는 이번 구현 범위가 아니다. 정상 실행에서 새로 생긴 다음 단계 대기열은 통합 실행 서비스나 향후 운영 스케줄에서 넘겨야 한다. 복구 스케줄러가 모든 신규 대기 장소를 임의로 실행하지는 않는다.

## 2. 데이터와 트랜잭션 경계
| 저장 대상 | 역할 | 중요한 기록 |
|---|---|---|
| place_collection_jobs | 카카오 검색 구역 작업 | 검색 조건 스냅샷, 부모·자식, 수집 실행 키, 상태·시도 횟수·재시도 시각 |
| place_collection_temp | 원본과 정제 과정 | 공급자 ID, 주소·좌표, 네이버·블로그 자료, 단계·상태·오류·다음 재시도 |
| openai_batch_tagging_jobs | 원격 Batch 관리 | 원격 ID, 제출 공급자·모델, 원격 상태·마지막 조회, 수집 처리권 |
| ai_batch_tagging_items | 정확한 제출 대상과 반영 여부 | Job·Temp ID, 요청 custom_id, PENDING/COMPLETED/FAILED |
| places / place_tags | 실제 추천에 사용하는 장소 | 최종 타입·태그·가중치, 점수·키워드·이미지·링크, needs_review |

외부 API 호출 전체에 DB 트랜잭션을 걸지 않는다. 선점과 상태 저장은 짧은 Writer 트랜잭션으로 수행한다. 카카오 페이지 저장, 네이버 한 장소 저장, AI 한 장소와 manifest 완료 기록은 각각 원자적으로 처리한다.

AI 저장 중 Place INSERT 후 태그 INSERT가 실패하면 그 장소의 Place·태그·Temp 완료·manifest 완료가 함께 롤백된다. 실패 기록은 별도 트랜잭션에서 남겨 다음 정상 장소를 계속 처리한다.

공급자와 provider_place_id의 유니크 제약으로 같은 외부 ID는 갱신한다. 이름·주소가 비슷한 서로 다른 공급자 장소를 자동 병합하는 기능은 없다. OPERATOR는 제공자 값만 준비되어 있으며 운영진 등록 API와 전용 ID 발급은 아직 없다.

## 3. 카카오: 검색 구역을 잃지 않는 수집
collectCity 또는 collectArea는 활성 KAKAO 프로필을 읽는다. 프로필의 검색 방식은 CATEGORY 또는 KEYWORD이며, 검색 조건을 Job에 복사해 이후 프로필 변경과 실행 중 작업을 분리한다.

initialize는 Area 행을 잠그고 해당 실행의 전체 프로필 root를 먼저 저장한다. 중단된 실행이 있으면 그 실행을 재사용하고 빠진 프로필을 보충한다. 이전 실행이 모두 종료된 뒤 명시적으로 수집 API를 다시 호출하면 새 collection_run_key로 수집한다. 복구 스케줄러는 새 실행을 만들지 않는다.

| 검색 정책 | 현재 구현 |
|---|---|
| 페이지 크기 | 15개 |
| 포화 판단 | total_count > pageable_count |
| 큰 구역 분할 | 긴 변 약 500m 초과 구역은 4등분 |
| 작은 구역 분할 | 그 이하에서는 긴 축 기준 2등분 |
| 최소 검색 구역 | 긴 변 약 50m |
| 페이지 순회 | 첫 페이지 저장 후 is_end 또는 최대 45페이지까지 |
| 최소 구역 포화 | 수집 가능한 결과 저장 후 PARTIAL·제한 오류 기록 |

분할은 부모 SPLIT 전환과 모든 자식 저장을 한 트랜잭션으로 처리한다. 자식 일부 저장 중 DB 오류가 나면 부모와 자식 모두 롤백된다. 구버전에서 일부 자식만 생성된 Job도 동일 경계를 대조해 빠진 자식을 보완한다.

검색 결과는 법정동 매핑으로 서비스 Area에 연결한다. 매핑되지 않거나 ID·좌표가 없는 항목은 저장하지 않는다. 같은 카카오 ID의 Temp가 있으면 원본을 갱신하고 네이버·AI 처리 단계와 상태는 유지한다.

겹치는 구역에서 같은 신규 ID가 동시에 들어오는 문제를 막기 위해 페이지 저장 동안 PostgreSQL advisory lock을 사용한다. 기존 Temp도 잠그며, DynamicUpdate로 불필요한 전체 필드 갱신을 줄인다.

카카오의 일시적 통신·속도 제한·DB 저장 실패는 시도 횟수 3 미만일 때 5분 후, 그 이후에는 24시간 후 재시도한다. 명시적 사용량 소진은 초기화 시각 또는 24시간 하한을 사용한다. 인증·잘못된 요청·분류 불가 오류는 운영 확인 대상으로 남긴다.

### 중단 예시
1·2페이지를 저장하고 3페이지 처리 중 앱이 종료되면 1·2페이지는 남는다. 단일 인스턴스 시작 복구가 해당 구역을 FAILED / COLLECTION_INTERRUPTED로 표시하고 즉시 재시도 대상으로 예약한다. 복구 worker는 그 구역의 1페이지부터 다시 검색한다. 이미 수집된 ID는 갱신하므로 새 장소 중복 INSERT를 피한다.

완료·PARTIAL 구역을 복구 때문에 다시 검색하지 않는다. 페이지 번호부터 이어가는 체크포인트는 구현하지 않았다. 중단 구역의 앞 페이지 재조회에 따른 API 호출은 일부 발생한다.

## 4. 네이버: 장소 단위 보강
refinePendingByArea는 대기 장소와 재시도 가능한 실패를 조회한다. tryStart가 Temp 한 건을 잠그고 PROCESSING으로 선점한 뒤 외부 호출을 수행한다.

첫 검색은 상호명과 지번주소에서 추출한 동네명으로 구성한다. 기존 규칙에 따라 성수동1가는 성수동으로 정리한다. 후보를 확정하지 못하면 상호명과 도로명주소 또는 전체 지번주소로 한 번 더 검색한다.

| 매칭 조건 | 동작 |
|---|---|
| 주소 일치 + 유사 상호 하나 | 매칭 후보로 선택 |
| 주소·유사 상호 후보 여러 개 | 카테고리로 좁히고 여전히 여러 개면 모호 오류 |
| 주소만 같고 상호가 다름 | 자동 선택하지 않음 |
| 이름·업종 유사, 주소 다름 | 이전 의심 후보로 기록하고 FAILED |
| 검색 결과 없음 | 결과 없음 오류 기록 |

한글·영문처럼 이름의 문자 체계가 달라 문자열 비교로 유사성을 확인하지 못하면 주소가 같아도 자동 매칭하지 않는다. 오매칭을 줄이기 위한 보수적 처리이며 해당 사례는 운영 확인이 필요하다.

지역 검색 성공 후 네이버 등록 상호와 동네명을 이용해 블로그 자료를 조회한다. 기존 EvidenceFilter를 통과한 제목·요약을 저장한다. 블로그 실패는 별도 상태·오류로 기록하고 지역 정제는 성공시켜 AI 단계로 넘긴다. 따라서 블로그 없이 AI가 평가할 수 있으며, 블로그 실패 자체를 별도 재조회하는 worker는 없다.

네이버 성공은 NAVER_ENRICHMENT / COMPLETED가 아니라 AI_TAGGING / PENDING으로 전환된다. 중단된 NAVER_ENRICHMENT / PROCESSING은 재시작 시 PENDING과 중단 사유로 복구한다. recovery worker는 이 복구 대상 및 예약된 실패만 실행한다.

일반 임시 오류와 429는 기존처럼 시도 횟수 3 미만인 동안 5분 후 재시도한다. 명시적 사용량 소진이 확인되면 초기화 시각 또는 24시간 하한을 사용하되 최대 시도 제한은 유지한다. 인증·설정·매칭 오류는 자동 재시도 대상이 아니다.

## 5. AI: 제출·수집·최종 저장
| 단위 | 크기와 의미 |
|---|---|
| 장소 | Temp 한 건 |
| Responses 요청 | 장소 최대 10개를 한 JSON 입력으로 전송 |
| JSONL | 요청 하나당 한 줄. custom_id로 제출 대상을 식별 |
| Batch Job | 장소 최대 1,000개. 1,000개라면 요청 최대 100줄 |

claim은 Temp 선점·Job 생성·정확한 요청별 대상 manifest를 같은 트랜잭션에 저장한다. 제출 당시 공급자와 모델도 Job에 보관한다. 제출 준비 중 오류도 핸들러를 거쳐 실패 상태로 남긴다.

파일 업로드 후 OPENAI는 processed까지 짧게 확인한다. OMNIROUTE는 기존처럼 파일 status 확인을 생략한다. Batch 생성은 completion_window=24h로 요청한다. 반환한 원격 Batch ID를 저장하면 SUBMITTED가 된다.

### 전달하는 자료와 분류
AI에는 후보 placeType, 직접 매칭된 matchedTypeRule, 전체 활성 typeRules, availableTags, 그리고 상호·주소·카카오/네이버 카테고리·블로그 자료를 전달한다. matchedTypeRule이 있으면 서버 저장 단계에서도 해당 타입을 우선한다. 이번 작업은 이 분류 정책을 바꾸지 않았다.

keywords는 MEAL의 대표 음식·전문 분야에 한해 저장한다. 다른 타입은 기존처럼 비운다. 점수는 experience와 distinctiveness 조정값 -25~25를 받아 각각 기준점 25를 더해 0~50으로 저장한다. 총 추천 우선도는 두 필드 합으로 계산하며 별도 합계 컬럼은 없다.

| 최종 PlaceType | 하나라도 있으면 needs_review=true인 태그 |
|---|---|
| CAFE | MARKET, BOARD_GAME, ESCAPE_ROOM, KOREAN_FOOD, JAPANESE_FOOD, WESTERN_FOOD, ASIAN_FOOD |
| MEAL | CAFE, BOARD_GAME, ESCAPE_ROOM |
| ACTIVITY | CAFE, KOREAN_FOOD, JAPANESE_FOOD, WESTERN_FOOD, ASIAN_FOOD, RESTAURANT |

충돌 태그는 검토 근거로 보존한다. 검토 표시는 장소를 제외하거나 타입을 바꾸지 않는다. MEAL에서 구체적 음식 태그가 없으면 RESTAURANT를 보완하고, 구체적 음식 태그와 중복되는 RESTAURANT만 정리한다. 재태깅 시 needsReview도 다시 계산한다. 검토 완료 처리 화면·API는 이번 범위에 포함되지 않는다.

### 결과 수집과 재수집
수집기는 DB에서 처리권 토큰과 15분 lease를 확보한다. 같은 Job의 다른 수집자는 원격 호출을 시작하지 못한다. lease는 처리 중 갱신하고, 장소 반영 시에도 소유권을 확인한다. 외부 호출은 연결 10초·읽기 60초로 제한한다.

원격이 아직 실행 중이면 조회를 유지한다. 25시간을 넘겨도 자동 취소하거나 새 배치를 만들지 않고 장기 실행 로그를 남긴다. last_polled_at과 remote_status로 확인할 수 있다.

completed뿐 아니라 failed·expired·cancelled에서도 제공되는 output/error 파일을 먼저 회수한다. 성공·실패 줄을 각각 처리하며, 최상위 error와 response.body.error를 모두 읽는다. 응답 거절·출력 미완료·JSON 구조 오류·개별 점수·태그 오류를 구분한다.

각 줄의 custom_id에 해당하는 실제 Temp ID 목록을 대조한다. ID 범위에 포함된다는 이유만으로 다른 요청의 장소를 허용하지 않는다. 중복 ID는 해당 장소를 실패 처리하고, 누락 ID는 별도 오류로 남긴다. 다른 요청 ID는 저장하지 않는다.

Place·태그·Temp 완료와 manifest COMPLETED는 한 트랜잭션에 저장된다. 결과를 재수집하면 이미 COMPLETED 또는 FAILED인 항목은 반영하지 않는다. 완료된 장소는 운영자가 수정했더라도 같은 배치의 재수집으로 덮어쓰지 않는다.

개별 장소 실패는 별도 트랜잭션으로 FAILED 처리하고 나머지를 계속 저장한다. 최종 Job은 실패 장소가 없으면 COMPLETED, 있으면 FAILED가 된다. 여러 오류가 섞였으면 AI_BATCH_PARTIAL_FAILURE로 요약하고 장소별 원인은 Temp와 manifest에서 확인한다.

collect API의 true는 종료된 결과 파일 처리가 마무리됐다는 뜻으로 사용된다. 모든 장소의 성공 여부는 Job·manifest 상태를 함께 확인해야 한다. 실행 중·처리권 미확보·조회 보류 등에서는 false를 반환한다.

## 6. 오류 핸들러와 재시도 정책
| 구성 | 책임 |
|---|---|
| KakaoCollectionErrorHandler | 카카오 작업 오류 분류와 Job 실패 기록 요청 |
| NaverRefinementErrorHandler | 네이버 매칭·외부 오류 분류와 Temp 실패 기록 요청 |
| AiTaggingErrorHandler | AI 제출·요청·개별 장소 오류 분류와 처리 범위 연결 |
| CollectionFailureClassifier | HTTP·예외 원인·명시적 오류코드 및 시간 정보 해석 |
| CollectionErrorPolicy | 허용 오류 목록과 다음 재시도 시각 결정 |
| 단계별 Writer | 오류·상태·next_retry_at을 짧은 트랜잭션으로 저장 |
| Scheduler | 예약 시각이 지난 대상만 실행 |

GlobalExceptionHandler는 HTTP 응답을 만드는 역할이다. 스케줄러의 수집 오류 처리에는 위 핸들러를 사용한다. 스케줄러 SQL 안에서 대기 시간을 다시 계산하지 않고 저장된 next_retry_at을 사용한다.

| AI 오류 | 재시도 정책 |
|---|---|
| 임시 통신·외부 서버, 일반 속도 제한 | 5분 간격 재시도 3회, 이후 24시간 간격 |
| JSON 파싱·결과 누락/중복·태그·점수·DB 오류 | 같은 재시도 정책 |
| 명시적 사용량 소진 | 미래 resets_at / resets_in_seconds가 있으면 그 이후 1분 여유, 없으면 24시간 |
| 인증·설정·잘못된 요청 | 자동 재제출 제외 |
| 응답 정책 거절 | 자동 재제출 제외·운영 확인 |
| 접수 여부 불명 | 자동 재제출 제외·원격 접수 대조 |
| 계속 실행 중인 원격 Batch | 기존 배치 조회 유지, 강제 재제출하지 않음 |

초기 제출 실패 당시 ai_retry_count=0이면 5분 후 첫 재시도를 예약한다. 재시도 제출 때 1, 2, 3으로 증가하며 3번째 재시도도 실패하면 이후 24시간 간격을 사용한다. 성공하면 0으로 초기화한다. 날짜가 바뀌었다는 이유만으로 즉시 실행하지 않는다.

사용량 초기화 시각과 Retry-After, OmniRoute cooldown은 구분한다. 일반 속도 제한의 Retry-After는 재시도 하한으로 반영할 수 있으나, 실제 quota reset이라고 추정하지 않는다. 현재 파이프라인이 받는 JSONL에 명시적 reset 필드가 없다면 24시간 fallback이 작동한다.

조회 중 인증·설정·영구 요청 오류는 Job의 오류에 기록하고 자동 polling을 보류한다. 원격이 실행 중일 수 있으므로 Temp를 임의 실패시키거나 새 배치를 제출하지 않는다. 설정 수정 후 수동 collect로 조회를 재개할 수 있다.

## 7. 상태 전환표
| 대상 | 정상 전환 | 실패·복구 전환 |
|---|---|---|
| 카카오 Job | READY → RUNNING → SPLIT / COMPLETED / PARTIAL | RUNNING → FAILED, 재시도 가능 시 예약 후 RUNNING |
| 네이버 Temp | NAVER_ENRICHMENT/PENDING → PROCESSING → AI_TAGGING/PENDING | PROCESSING → FAILED, 재시작 시 중단 건은 PENDING |
| AI Temp | AI_TAGGING/PENDING → PROCESSING → COMPLETED | PROCESSING → FAILED, 예약된 실패만 새 배치에서 PROCESSING |
| AI Job | SUBMITTING → SUBMITTED → COMPLETED | 제출 실패 또는 일부 결과 실패 시 FAILED |
| AI manifest | PENDING → COMPLETED 또는 FAILED | 같은 배치 재수집 시 종료된 항목은 유지 |

AI_TAGGING / COMPLETED는 Place·PlaceTag 저장까지 완료된 상태다. 별도 COLLECTED 상태를 추가하지 않았다. 실패한 장소는 이전 Job 전체를 다시 실행하는 것이 아니라, 재시도 가능한 장소만 새 Job으로 묶는다.

## 8. 중단 시나리오와 복구
| 중단 지점 | 남는 기록 | 복구 방법 |
|---|---|---|
| 카카오 프로필 수집 전 | 전체 프로필 root가 이미 존재 | READY 작업 재개 |
| 카카오 분할 도중 | 원자 저장으로 부모·자식 모두 저장 또는 롤백 | 미완료 부모/자식 구역 재실행 |
| 카카오 페이지 저장 후 | 완료 페이지의 Temp 유지 | 중단 구역 1페이지부터 재검색 |
| 네이버 외부 호출 중 | Temp PROCESSING | 단일 인스턴스 시작 복구 후 해당 장소 재조회 |
| AI 원격 ID 저장 전 | Job·manifest·Temp 선점 | 접수 여부 불명으로 보존, 자동 재제출 금지 |
| AI 원격 ID 저장 후 | SUBMITTED와 제출 공급자 보존 | 같은 공급자로 기존 Batch 조회 |
| AI 일부 장소 저장 후 | 완료 manifest와 Place 유지 | 파일 재수집 시 완료분 건너뛰고 미완료분 반영 |
| 모든 장소 저장 후 Job 종료 전 | manifest 전체 종료, Job SUBMITTED | 재수집 후 Job만 종료 처리 |

Spring이 내려가도 별도 OmniRoute/OpenAI 배치는 계속 실행할 수 있다. 재개는 모델의 문장 생성 중간부터 이어가는 기능이 아니다. 기존 원격 배치 결과 회수 또는 실패한 장소의 새 요청 실행을 의미한다.

OmniRoute 자체가 종료된 경우의 내부 배치 재개는 외부 공급자 동작이다. 우리 서비스는 저장된 원격 ID를 조회한다. 공급자가 계속 실행 중이라고만 응답하면 강제 실패하지 않고 운영 확인이 필요하다.

단일 인스턴스 시작 복구는 카카오 READY/RUNNING을 중단 실패로 예약하고, 네이버 PROCESSING을 복구 대기로 바꾼다. 원격 ID가 있는 AI 작업은 조회를 유지하며, ID 없는 SUBMITTING은 접수 여부 불명으로 표시한다. 종료된 프로세스의 수집 lease도 해제한다.

## 9. 오류코드 사전
새 코드와 기존 코드명을 함께 보존했다. 과거의 포괄적인 오류를 새 코드로 임의 추정해 변환하지 않는다. V14는 의미가 확실한 기존 임시 오류와 파싱·검증 오류에만 재시도 시각을 보충한다.

| 오류코드 | 의미 | 기본 처리 |
|---|---|---|
| COLLECTION_INTERRUPTED | 앱 종료로 카카오 Job 또는 네이버 단건 처리가 중단됨 | 카카오 재시도 / 네이버 대기 복구 |
| KAKAO_API_AUTH_FAILED | 카카오 인증 또는 접근 권한 오류 | 자동 재제출 제외·운영 확인 |
| KAKAO_API_INVALID_REQUEST | 카카오가 거절한 잘못된 외부 요청 | 자동 재제출 제외·운영 확인 |
| KAKAO_API_TEMPORARY_FAILURE | 카카오 통신 오류, HTTP 408 또는 5xx | 카카오 재시도 예약 |
| KAKAO_API_RATE_LIMITED | 카카오의 짧은 호출 속도 제한 | 카카오 재시도 예약 |
| KAKAO_API_QUOTA_EXHAUSTED | 명시적인 카카오 사용량 한도 소진 | 카카오 재시도 예약 |
| NAVER_API_INVALID_REQUEST | 네이버가 거절한 잘못된 외부 요청 | 자동 재제출 제외·운영 확인 |
| NAVER_API_QUOTA_EXHAUSTED | 명시적인 네이버 사용량 한도 소진 | 네이버 재시도 예약 |
| AI_API_AUTH_FAILED | AI 공급자 인증·접근 권한 오류 | 자동 재제출 제외·운영 확인 |
| AI_API_INVALID_REQUEST | AI 외부 요청 또는 서버의 요청 구성 오류 | 자동 재제출 제외·운영 확인 |
| AI_API_TEMPORARY_FAILURE | AI 통신 오류, HTTP 408 또는 5xx | AI 재시도 예약 |
| AI_API_RATE_LIMITED | AI의 짧은 요청 속도 제한 | AI 재시도 예약 |
| AI_API_QUOTA_EXHAUSTED | usage_limit_reached 등으로 확인한 사용량 소진 | AI 재시도 예약 |
| AI_BATCH_EXPIRED | 원격 Batch가 만료되어 남은 결과가 없음 | AI 재시도 예약 |
| AI_BATCH_CANCELLED | 원격 Batch가 취소되어 남은 결과가 없음 | AI 재시도 예약 |
| AI_RESPONSE_REFUSED | AI 응답 또는 요청이 정책상 거절됨 | 자동 재제출 제외·운영 확인 |
| AI_RESPONSE_INCOMPLETE | 원격 응답이 incomplete로 종료됨 | AI 재시도 예약 |
| AI_RESPONSE_MISSING | 제출한 장소의 결과가 응답에서 누락됨 | AI 재시도 예약 |
| AI_RESPONSE_DUPLICATE | 같은 요청 안에 동일한 장소 ID 결과가 중복됨 | AI 재시도 예약 |
| AI_STORAGE_FAILED | AI 결과의 DB 저장 실패. 해당 장소 트랜잭션 롤백 | AI 재시도 예약 |
| KAKAO_STORAGE_FAILED | 카카오 검색 결과의 DB 저장 실패 | 카카오 재시도 예약 |
| NAVER_STORAGE_FAILED | 네이버 결과의 DB 저장 실패 | 네이버 재시도 예약 |
| AI_SCORE_VALIDATION_FAILED | 점수 조정값 누락, 정수 아님, 또는 -25~25 범위 위반 | AI 재시도 예약 |
| AI_COLLECTION_INTERRUPTED | AI 결과 반영 중단용 호환 오류코드. 현재 재개는 manifest로 처리 | AI 재시도 예약 |
| AI_BATCH_PROVIDER_UNRESOLVED | 기존 배치의 공급자 또는 제출 manifest 확인 필요 | 자동 재제출 제외·운영 확인 |
| AI_BATCH_PARTIAL_FAILURE | 서로 다른 원인으로 여러 장소가 실패한 Job 요약 | 장소별 사유 확인 |
| KAKAO_API_KEY_NOT_CONFIGURED | 카카오 API 키 설정 없음 | 자동 재제출 제외·운영 확인 |
| KAKAO_LOCAL_API_REQUEST_FAILED | 세부 원인을 확인할 수 없는 기존 카카오 API 요청 실패 | 자동 재제출 제외·운영 확인 |
| KAKAO_LOCAL_API_EMPTY_RESPONSE | 카카오 응답 본문 없음 | 자동 재제출 제외·운영 확인 |
| KAKAO_RESULT_LIMIT_EXCEEDED | 최소 검색 구역에서도 검색 결과 수 제한에 걸림 | PARTIAL 유지·운영 확인 |
| KAKAO_COLLECTION_PROCESSING_FAILED | 분할·응답 처리 중 예상하지 못한 오류 | 자동 재제출 제외·운영 확인 |
| NAVER_RESULT_NOT_FOUND | 지역 검색 결과 없음 | 자동 재제출 제외·운영 확인 |
| NAVER_PLACE_NOT_MATCHED | 주소와 유사 상호를 함께 만족하는 후보 없음 | 자동 재제출 제외·운영 확인 |
| NAVER_PLACE_AMBIGUOUS | 같은 주소·유사 이름 후보가 여러 개여서 확정 불가 | 자동 재제출 제외·운영 확인 |
| NAVER_POSSIBLE_RELOCATION | 이름·업종은 유사하지만 주소가 다른 이전 의심 후보 | 자동 재제출 제외·운영 확인 |
| NAVER_SEARCH_API_KEY_NOT_CONFIGURED | 네이버 검색 인증 설정 없음 | 자동 재제출 제외·운영 확인 |
| NAVER_LOCAL_API_REQUEST_FAILED | 세부 원인 없는 기존 네이버 지역 검색 실패 | 자동 재제출 제외·운영 확인 |
| NAVER_LOCAL_API_EMPTY_RESPONSE | 네이버 지역 검색 응답 본문 없음 | 자동 재제출 제외·운영 확인 |
| NAVER_BLOG_API_REQUEST_FAILED | 세부 원인 없는 기존 블로그 검색 실패 | 자동 재제출 제외·운영 확인 |
| NAVER_BLOG_API_EMPTY_RESPONSE | 블로그 검색 응답 본문 없음 | 자동 재제출 제외·운영 확인 |
| NAVER_API_REQUEST_FAILED | 세부 원인을 확인할 수 없는 기존 네이버 API 실패 | 자동 재제출 제외·운영 확인 |
| NAVER_API_TEMPORARY_FAILURE | 네이버 통신 오류, HTTP 408 또는 5xx | 네이버 재시도 예약 |
| NAVER_API_RATE_LIMITED | 네이버 HTTP 429 속도 제한 | 네이버 재시도 예약 |
| NAVER_API_AUTH_FAILED | 네이버 HTTP 401·403 인증 오류 | 자동 재제출 제외·운영 확인 |
| OPENAI_API_KEY_NOT_CONFIGURED | 선택한 AI 공급자의 설정 누락. 기존 코드명 보존 | 자동 재제출 제외·운영 확인 |
| OPENAI_API_REQUEST_FAILED | 세부 원인을 복원할 수 없는 기존 AI API 오류 | 자동 재제출 제외·운영 확인 |
| OPENAI_API_EMPTY_RESPONSE | AI 파일 업로드 등에서 기대 필드·응답 없음 | AI 재시도 예약 |
| OPENAI_BATCH_SUBMISSION_FAILED | 기존 배치 제출 실패 코드. 새 원인은 구체적으로 분류 | 자동 재제출 제외·운영 확인 |
| OPENAI_BATCH_SUBMISSION_UNKNOWN | 원격이 접수했는지 확정할 수 없음. 중복 제출 금지 | 자동 재제출 제외·운영 확인 |
| OPENAI_BATCH_REMOTE_FAILED | 원격 배치 실패. 구체적 오류 근거가 없어 운영 확인 필요 | 자동 재제출 제외·운영 확인 |
| OPENAI_BATCH_TEMPORARY_FAILURE | 기존 일시적 AI 요청 실패 코드. 재시도 호환 유지 | AI 재시도 예약 |
| OPENAI_BATCH_REQUEST_REJECTED | 기존 원격 요청 거절 코드. 원인 수정 필요 | 자동 재제출 제외·운영 확인 |
| AI_NO_ACTIVE_TAGS | 활성 태그가 없어 요청 생성 불가 | 자동 재제출 제외·운영 확인 |
| AI_RESPONSE_PARSE_FAILED | 결과 JSON 또는 개별 장소 DTO를 읽을 수 없음 | AI 재시도 예약 |
| AI_RESPONSE_TARGET_MISMATCH | 기존 결과 대상 불일치 코드. 새 코드는 요청별 ID 대조 | AI 재시도 예약 |
| AI_TAG_VALIDATION_FAILED | 태그 수·중복·등록 여부·가중치 등의 검증 실패 | AI 재시도 예약 |
| AI_BATCH_OUTPUT_INVALID | 결과 구조 오류 또는 요청을 식별할 수 없는 결과 | AI 재시도 예약 |
| AI_RESPONSE_INVALID | 기존 AI 응답 계약 위반 코드 | AI 재시도 예약 |
| INVALID_PROCESSING_STEP | 현재 Temp 단계에서 실행할 수 없는 요청 | 자동 재제출 제외·운영 확인 |
| UNEXPECTED_ERROR | 분류되지 않은 내부 오류. 원인 조사 필요 | 자동 재제출 제외·운영 확인 |

블로그 오류는 같은 코드라도 naver_blog_error_code에 보관되며, 지역 정제 실패를 의미하지 않을 수 있다. Job 요약 오류와 개별 장소 오류도 구분해 읽어야 한다.

## 10. 스케줄러·설정·수동 실행
| 설정 | 기본값 | 의미 |
|---|---|---|
| PLACE_COLLECTION_RECOVERY_SINGLE_INSTANCE | false | 수집 앱 한 개일 때 시작 시 중단 작업 복구 |
| PLACE_COLLECTION_SCHEDULING_ENABLED | true | 자동 복구·AI 재시도·결과 조회 worker 활성화 |
| place-collection.kakao.recovery-poll-millis | 60000 | 카카오 기존 작업 회수, 주기당 최대 100 Job |
| place-collection.naver.recovery-poll-millis | 60000 | 네이버 중단·예약 실패 회수, 주기당 최대 100 장소 |
| place-collection.ai.batch.submit-poll-millis | 60000 | 신규·재시도 가능한 AI 태깅 장소 제출 확인 주기, 주기당 최대 1,000 장소 (환경변수: `PLACE_COLLECTION_AI_BATCH_SUBMIT_POLL_MILLIS`) |
| place-collection.ai.batch.poll-interval-millis | 60000 | SUBMITTED 배치 결과 확인 |

fixedDelay이므로 이전 실행 종료 후 설정 시간만큼 기다린다. 실제 재시도는 next_retry_at 이후 worker가 확인하는 시점에 실행되며 정확한 초 단위 예약은 아니다. 기본 Spring 스케줄러 실행 스레드 설정은 기존 구성을 따른다.

| 수동 API / 서비스 | 용도 |
|---|---|
| POST /internal/place-collection/test/areas/{areaId} | 카카오 → 네이버 → AI 제출을 순서대로 실행하는 테스트 진입점 |
| POST /internal/place-collection/batch/ai | 신규 AI 대기와 재시도 가능한 실패 제출 |
| POST /internal/place-collection/batch/ai/{jobId}/collect | 원격 상태 확인과 결과 반영. 조회 보류 후 수동 재개에도 사용 |
| POST /internal/place-collection/batch/ai/re-tag | 이미 완료한 장소의 명시적 재태깅. 실패 재시도와 다름 |
| collectCity / collectArea | 카카오 수집 시작·중단 실행 재사용 |

실제 외부 URL에는 SERVER_CONTEXT_PATH 설정값이 앞에 붙는다. 운영자가 조회하는 DB의 로컬 Job ID와 공급자의 원격 Batch ID를 혼동하지 않는다. API의 deprecated 표시는 Swagger 구분용이며 API 사용을 차단하는 보안 기능은 아니다.

## 11. 운영 확인과 마이그레이션
기존 V1~V12 이력은 수정하지 않았다. 이전 작업의 V13(needs_review·OPERATOR)을 보존하고, 이번 V14에 재시도 시각·카카오 실행 키·AI 공급자/모델·조회 기록·수집 lease·제출 manifest를 추가했다. 기존 데이터 삭제나 운영 DB 초기화는 수행하지 않았다.

배포 전에는 기존 미완료 배치의 공급자와 원본 입력 JSONL을 대조해야 한다. 구버전 Job에는 제출 공급자와 정확한 manifest가 없으므로 현재 설정을 과거 제출 정보로 단정하지 않는다. 해당 작업은 AI_BATCH_PROVIDER_UNRESOLVED로 조회 보류하며 Temp를 임의 실패시키지 않는다.

운영자는 공급자와 원본 입력에 실제 포함된 custom_id·Temp ID 목록을 확인한 뒤 기록을 보충해야 한다. 일부 저장된 구버전 배치라면 완료한 결과도 원본과 대조한다. 검증되지 않은 ID 범위로 manifest를 만들어서는 안 된다.

| 확인할 상황 | 운영자가 볼 정보 | 조치 |
|---|---|---|
| 접수 여부 불명 | 로컬 Job, manifest, 공급자 배치 목록·원본 입력 | 중복 제출 전 실제 접수 여부 확인 |
| 공급자/대상 기록 미확인 | provider, 원격 ID, 입력 custom_id·Temp ID | 근거 확인 후 연결 보완 |
| 인증·설정 오류 | Job error_code, provider, 선택 설정 | 설정 수정 후 수동 collect 또는 실패 장소 재실행 |
| 장기 원격 실행 | remote_status, last_polled_at, created_at | 원격 대시보드 확인. 실행 중이면 조회 유지 |
| 반복 검증·DB 실패 | Temp error_code, next_retry_at, manifest | 데이터·DB 제약·응답 계약 점검 |
| 타입·태그 충돌 | needs_review와 최종 태그 | 실제 장소 검토 후 별도 운영 수정 |

### 읽기 전용 확인 SQL
```sql
SELECT processing_step, status, error_code, COUNT(*)
FROM place_collection_temp
GROUP BY processing_step, status, error_code
ORDER BY processing_step, status, error_code;

SELECT id, provider, status, remote_status, last_polled_at,
       error_code, requested_count
FROM openai_batch_tagging_jobs ORDER BY id DESC;

SELECT job_id, request_key, status, error_code, COUNT(*)
FROM ai_batch_tagging_items
GROUP BY job_id, request_key, status, error_code;
```

## 12. 검증 결과와 확인 범위
전체 테스트 117건 중 116건 통과, 1건 생략, 실패 0건·오류 0건이다. 생략한 항목은 실제 모델 호출을 요구하는 OmniRoute live 테스트다. (2026-11-13 12:42 KST)

| 검증 | 방식 | 결과 |
|---|---|---|
| 기본 분류·태그·점수·기존 추천 기능 | 기존 전체 회귀 테스트 | 통과 |
| 요청/장소 일부 실패, 잘못된 ID·점수·JSON | 실제 수집 코드 + 모의 원격 응답 | 정상 결과 계속 반영 경로 확인 |
| DB 태그 저장 실패 | 전용 PostgreSQL trigger로 INSERT 실패 주입 | 해당 Place 저장 롤백, 다른 장소 성공 |
| 부분 저장 후 중단 | 첫 결과 commit 후 Error 주입, 재시작 상태 재현 | 완료 장소 유지, 미완료 장소만 저장 |
| 모든 결과 저장 후 Job 미종료 | Job 상태를 미종료로 재현 | 중복 Place 생성 없이 Job 종료 |
| 동시 수집 | 두 스레드가 같은 Job 처리권 요청 | 한 수집자만 처리권 획득 |
| 카카오 분할 중단·저장 실패 | 전용 DB 분할 INSERT 실패 주입 | 자식 전체와 부모 전환 롤백 |
| 카카오 root·자식 재개 | 실제 Job 생성 후 시작 복구 실행 | 동일 실행·미완료 구역 재사용 |
| 네이버 중단 복구 | PROCESSING/완료 장소 상태 재현 | 중단 장소만 대기 복구 |
| 원격 접수 후 로컬 ID 저장 실패 | 원격 mock 성공 + Job UPDATE 실패 trigger | 접수 여부 불명, 자동 재제출 제외 |
| AI 24시간·초기화 시각 | 정책 단위 테스트·모의 오류 | 짧은 재시도, 24시간 fallback, reset 하한 확인 |
| 클라이언트 요청·파일 조회 | Spring MockRestServiceServer | 실제 요청 생성·응답 해석 회귀 통과 |

전용 테스트 DB course_recovery_test를 새 컨테이너에서 사용했다. 운영 DB는 초기화하거나 테스트 대상으로 사용하지 않았다. 중단 테스트는 Error·DB 실패·잔여 lease 상태를 주입한 재현이며 OS에서 실제 프로세스를 강제 종료한 실험은 아니다.

OmniRoute는 홈서버 설치 소스에서 버전 3.8.53, Codex 사용량 window의 resetAt 표현, 활성 배치 DB 조회 구조를 확인했다. 다른 프로젝트가 사용 중이므로 OmniRoute를 실제 재시작하지 않았고 새 모델 요청도 보내지 않았다. 배치 JSONL에 reset 필드가 실제 전달되는지와 프로세스 종료 후 내부 worker 재개는 실시간 검증하지 않았다.

공개 참고 자료: OmniRoute 공식 저장소의 docs/architecture/RESILIENCE_GUIDE.md. cooldown과 실제 사용량 초기화는 별도 개념이다. 구현은 응답에 실제 존재하는 resets_at 또는 resets_in_seconds만 quota 초기화 근거로 사용한다.

## 13. 변경 전후와 코드 위치
| 변경 전 | 변경 후 |
|---|---|
| 서비스 곳곳에서 오류 분류·대기 간격 결정 | 단계별 Handler + 공통 정책 + next_retry_at |
| 카카오 자식 하나 생성 후 즉시 실행 | 부모·자식 전체 원자 저장 후 실행 |
| 카카오 중단 Job 실패 표시만 있음 | 기존 검색 구역을 회수하는 worker 연결 |
| 주소만 같은 네이버 한 후보도 선택 | 주소와 유사 상호를 함께 요구 |
| 한 AI 장소 오류가 같은 요청 후속 처리 차단 | 장소별 실패 격리 |
| 완료 Temp의 Job 연결 해제로 재수집 대상 불명 | 제출 manifest와 완료 기록 보관 |
| 현재 공급자 설정으로 과거 Batch 조회 | 제출 당시 공급자로 조회 |
| 원격 만료·취소 시 결과 파일 미수집 | 회수 가능한 성공 결과부터 저장 |
| 짧은 AI 재시도 후 48시간 | 3회 후 24시간, quota 초기화 정보 우선 |

| 클래스·메서드 | 역할 |
|---|---|
| KakaoAreaCollectionService.collectCity / collectArea / resumePendingJobs | 카카오 실행 진입·미완료 회수 |
| KakaoCollectionJobWriter.initialize / tryStart / split | 검색 계획·선점·분할 트랜잭션 |
| KakaoPlaceWriter.saveDocuments | 페이지 원본 저장·중복 ID 갱신 |
| PlaceRefinementService.refine / refineRecoverable | 네이버 정제·복구 대상 실행 |
| NaverRefinementWriter.tryStart / complete / fail | 네이버 단건 상태 저장 |
| OpenAiBatchJobWriter.claimPending / claimRetries | AI 대상 선점·manifest 생성 |
| OpenAiBatchTaggingService.collect / applyLine | 원격 조회·요청별 결과 검사 |
| AiBatchResultWriter.complete | Place 저장과 manifest 완료의 원자 반영 |
| AiTaggingWriter.complete | 타입·태그·점수·키워드·needsReview 저장 |
| CollectionErrorPolicy.nextRetry | 재시도 시간과 제외 정책 |
| PlaceCollectionOrchestrator.runArea | 테스트 통합 실행의 카카오→네이버→AI 연결 |
| PlaceCollectionRestartRecovery.run | 단일 인스턴스 시작 복구 |
| pipeline/scheduler의 KakaoCollectionRecoveryScheduler / NaverRefinementRecoveryScheduler / OpenAiBatchTaggingScheduler | 중단·실패 복구와 AI Batch polling |

핵심 코드는 `domain/place/collection/pipeline` 아래에 있다.

| 패키지 | 역할 |
|---|---|
| `kakao` | 카카오 검색·구역 분할·Temp 저장 |
| `naver` | 네이버 장소 매칭·블로그 보강 |
| `ai` | AI 입력 구성·Batch 제출·결과 반영 |
| `orchestration` | 단계 순서 조율, 앱 시작 시 중단 작업 복구 |
| `scheduler` | 단계별 복구·원격 배치 조회 주기 실행 |
| `support/error` | 장소 수집 도메인의 공통 오류 분류·재시도 정책 |
| `test` | Swagger 수동 테스트 컨트롤러 |

수동 통합 테스트 컨트롤러는 `PlaceCollectionOrchestrator`만 호출한다. 정기적인 신규 Area 수집 주기는 아직 연결하지 않았고, 기존 스케줄러는 재시도·복구·Batch 조회를 맡는다. 전체 변경 파일 목록과 테스트 요약은 별도 변경 보고서에 제공한다.

## 14. 남은 제한과 다음 단계
- 매일 수집할 Area 선정·개수·신규 대기열의 다음 단계 전달 일정은 후속 운영 스케줄 작업이다.
- 단일 인스턴스 시작 복구 옵션을 여러 수집 앱에서 동시에 켜면 안 된다. 다중 인스턴스의 카카오·네이버 강제 종료 회수에는 작업 소유권·heartbeat 확장이 필요하다.
- 원격 접수 여부 불명과 실행 중 배치 정체는 비용 중복을 피하기 위해 자동 재제출하지 않는다.
- 기존 진행 배치의 provider·manifest를 추측해 복원하지 않는다. 운영 대조가 필요하다.
- OmniRoute 자체 배치 재개와 실제 reset 전달은 실시간 장애 실험으로 확인되지 않았다.
- 장소의 의미적 분류 정확성은 테스트 통과만으로 보장되지 않는다. needs_review 검토와 실제 표본 확인을 함께 사용한다.
- 수집 시점과 결과 반영 사이에 태그 활성화·분류 규칙이 바뀌면 현재 저장 정책으로 검증한다. 정책 변경은 진행 배치를 확인하며 운영해야 한다.
- 검토 화면·운영진 등록·병합·전용 OPERATOR ID 발급은 이번 범위 밖이다.


예약 수집의 최신 구조와 설정은 [초기 수집 스케줄링 가이드](./scheduling-guide.md)를 참고한다. 기존 복구 스케줄러 패키지는 scheduling으로 재구성되었다.
