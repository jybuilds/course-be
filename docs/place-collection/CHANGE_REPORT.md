# 장소 수집 오류·복구 변경 보고서

## 패키지 구조 정리 (2026-11-13)
- 수집 코드를 `pipeline/kakao`, `pipeline/naver`, `pipeline/ai`로 기능별 이동.
- 테스트 API는 `pipeline/test`에 유지하고, 단계 연결은 `pipeline/orchestration/PlaceCollectionOrchestrator`로 이동. 기존 테스트 API 경로와 응답은 유지.
- 단계별 복구·Batch polling 스케줄러는 `pipeline/scheduler`로 이동. 신규 Area 정기 수집 주기는 추가하지 않음.
- 수집 전용 오류 정책은 `pipeline/support/error`에 둠. HTTP 공통 예외 처리 `global/exception`과 분리.

이전 변경 목록에서 스케줄러·수집 단계 파일 경로는 현재 기능별 패키지 위치를 가리키도록 갱신했다.

## 결과
- 단계별 카카오·네이버·AI 오류 핸들러와 공통 정책 연결.
- next_retry_at을 통한 재시도 예약. AI 5분 재시도 3회 이후 24시간, 사용량 소진은 명시 reset 또는 24시간.
- 카카오 root/child 작업 계획 원자 저장, 페이지 저장 유지, 미완료 구역 재개.
- 네이버 이름·주소 동시 매칭 및 단건 중단 복구.
- AI 공급자·모델/정확한 요청별 manifest/DB 수집 처리권 기록.
- 장소별 부분 실패 분리, 원자 저장, 성공 결과를 덮어쓰지 않는 재수집.
- 종료된 배치의 부분 성공 회수 및 인증 오류 polling 보류.
- 기존 진행 배치의 공급자·대상 목록은 추측하지 않고 운영 확인 보류.

## 검증
- 전용 PostgreSQL 컨테이너의 course_recovery_test에서 신규 마이그레이션 전체 적용 및 JPA validate 통과.
- 전체 117건 중 116건 통과, 1건 생략, 실패·오류 0건.
- 실제 DB trigger로 태그 INSERT와 분할 child INSERT 실패, 원격 접수 후 로컬 ID 저장 실패를 주입해 검증.
- 부분 저장 후 Error·잔여 lease 상태를 재현하고 재수집의 중복 방지 검증.
- 두 스레드의 동시 수집 처리권 획득 경쟁 검증.
- 실제 모델 요청·OmniRoute 프로세스 재시작은 하지 않음.
- 최종 PDF 19페이지 전체 렌더 검수. 목차, 한글, 오류 표 반복 헤더, 고아 페이지 제거 확인.

## 데이터·정책 보존
- 기존 V1~V12 마이그레이션 변경 없음. 이전 V13 보존, 이번 V14 추가.
- 운영 DB 초기화·데이터 삭제·운영 서비스 재시작 없음.
- .env 및 AI 프롬프트·평가 기준 변경 없음.
- 기존 needsReview·OPERATOR 코드와 테스트 보존.

## 배포 전 운영 확인
- V13/V14는 실제 운영 DB에 아직 적용하지 않음. 배포 시 Flyway가 적용해야 함.
- 구버전 미완료 AI Job은 공급자/원본 JSONL manifest 확인이 필요함. 확인 없이 현재 설정으로 과거 공급자를 추정하지 않음.
- 단일 수집 인스턴스에서 시작 복구를 사용하려면 PLACE_COLLECTION_RECOVERY_SINGLE_INSTANCE=true.
- 신규 Area 운영 일정과 신규 대기열 전달 스케줄은 후속 작업.
- OmniRoute 설치 소스 확인과 실시간 미검증 범위는 OMNIROUTE_EVIDENCE.md 참조.

## 재현 테스트 실행
```sh
DB_URL=jdbc:postgresql://127.0.0.1:15439/course_recovery_test \
DB_USERNAME=course_test DB_PASSWORD='<전용 테스트 DB 비밀번호>' \
PLACE_COLLECTION_SCHEDULING_ENABLED=false ./gradlew test --rerun-tasks
```

## 최종 변경 파일
이전 작업에서 이미 변경되어 있던 파일은 '기존 작업 보존'으로 표시한다.

| 파일 | 구분 |
|---|---|
| .env.example | 이번 변경 |
| compose.yaml | 이번 변경 |
| docs/place-collection/GUIDE.md | 이번 변경 |
| docs/place-collection/OMNIROUTE_EVIDENCE.md | 이번 변경 |
| docs/place-collection/PROGRESS.md | 이번 변경 |
| docs/place-collection/README.md | 이번 변경 |
| docs/place-collection/render_guide.py | 이번 변경 |
| docs/place-collection/verification-summary.json | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/entity/PlaceCollectionTemp.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/entity/PlaceRefinementErrorCode.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/ai/entity/OpenAiBatchTaggingJob.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/ai/service/AiBatchResultWriter.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/ai/service/AiTaggingErrorHandler.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/ai/service/OpenAiBatchJobWriter.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/scheduler/OpenAiBatchTaggingScheduler.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/ai/service/OpenAiBatchTaggingService.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/kakao/entity/PlaceCollectionJob.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/kakao/repository/PlaceCollectionJobRepository.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/kakao/service/KakaoAreaCollectionService.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/kakao/service/KakaoCollectionErrorHandler.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/kakao/service/KakaoCollectionJobWriter.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/scheduler/KakaoCollectionRecoveryScheduler.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/kakao/service/KakaoPlaceWriter.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/orchestration/PlaceCollectionRestartRecovery.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/ai/service/AiTaggingWriter.java | 기존 작업 보존 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/support/error/CollectionErrorPolicy.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/support/error/CollectionFailure.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/support/error/CollectionFailureClassifier.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/naver/service/NaverPlaceMatcher.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/naver/service/NaverRefinementErrorHandler.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/scheduler/NaverRefinementRecoveryScheduler.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/naver/service/NaverRefinementWriter.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/pipeline/naver/service/PlaceRefinementService.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/collection/repository/PlaceCollectionTempRepository.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/domain/place/entity/Place.java | 기존 작업 보존 |
| src/main/java/com/junsang/course_backend/domain/place/entity/PlaceProvider.java | 기존 작업 보존 |
| src/main/java/com/junsang/course_backend/infra/kakao/KakaoLocalClient.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/infra/openai/batch/OpenAiBatchClient.java | 이번 변경 |
| src/main/java/com/junsang/course_backend/infra/openai/config/AiProviderProperties.java | 이번 변경 |
| src/main/resources/db/migration/V13__add_place_review_flag_and_operator_provider.sql | 기존 작업 보존 |
| src/main/resources/db/migration/V14__collection_recovery_and_retry_policy.sql | 이번 변경 |
| src/test/java/com/junsang/course_backend/domain/place/collection/pipeline/ai/service/AiBatchRetryQueueTest.java | 이번 변경 |
| src/test/java/com/junsang/course_backend/domain/place/collection/pipeline/ai/service/CollectionRecoveryIntegrationTest.java | 이번 변경 |
| src/test/java/com/junsang/course_backend/domain/place/collection/pipeline/ai/service/OpenAiBatchTaggingServiceTest.java | 이번 변경 |
| src/test/java/com/junsang/course_backend/domain/place/collection/pipeline/kakao/service/KakaoAreaCollectionServiceTest.java | 이번 변경 |
| src/test/java/com/junsang/course_backend/domain/place/collection/pipeline/ai/service/AiTaggingWriterTest.java | 기존 작업 보존 |
| src/test/java/com/junsang/course_backend/domain/place/collection/pipeline/support/error/CollectionErrorPolicyTest.java | 이번 변경 |
| src/test/java/com/junsang/course_backend/domain/place/collection/pipeline/naver/service/NaverPlaceMatcherTest.java | 이번 변경 |
| src/test/java/com/junsang/course_backend/domain/place/collection/pipeline/naver/service/PlaceRefinementServiceTest.java | 이번 변경 |
| src/test/java/com/junsang/course_backend/infra/openai/batch/OmniRouteBatchLiveTest.java | 이번 변경 |
| docs/place-collection/CHANGE_REPORT.md | 이번 변경 |
