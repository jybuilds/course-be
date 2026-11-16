# 장소 수집 오류·복구 개선 진행 기록

목표: 2026-11-13 사용자 명세 전체 구현 및 코드 기반 PDF 가이드.
저장소: /Users/youjunsang/Programming/project/course/course_backend

## 보존 기준
- 기존 needsReview/OPERATOR 변경(Place, Provider, Writer, WriterTest, V13) 보존.
- 기존 마이그레이션 수정 금지. V14 이후 추가.
- 프롬프트/태그/점수 평가 변경 금지.
- 실제 DB 초기화·기존 운영 데이터 삭제·유료 대량 호출 금지.

## 조사 결과
- 카카오: saveAll 페이지별 커밋, 자식 생성/실행 순차, 페이지 체크포인트 없음. READY/RUNNING은 시작 복구에서 FAILED 처리하나 실행 큐 없음.
- 네이버: tryStart/complete/fail 단건 트랜잭션. 시작 복구 PROCESSING→PENDING. 임시/429만 5분 후 조회. 블로그 실패는 AI 진행 허용.
- AI: Job 선점 후 원격 제출, 대상 manifest 없음. 완료 Temp가 job 연결 해제되어 결과 재수집에 실패. 요청 단위 예외가 후속 장소 차단. 원격 종료 상태 시 부분 결과 미회수.
- AI 재시도: whitelist 단일 코드, 5분 3회 후 48시간. 단계별 handler 없음.
- 단일 인스턴스 시작 복구 옵션 사용. 운영 일정(매일 Area 개수)은 범위 밖.

## 실행 계획
1. 공통 오류 정책/오류 데이터와 단계별 핸들러, additive migration 및 retry 시각.
2. 카카오 root/child 원자 생성, 작업 선점/회수, 실패 큐, 이름·주소 매칭 보강.
3. AI durable manifest + 공급자 보관 + 수집 lease + 장소별 원자 저장/재수집.
4. 네이버 재시도·복구 공통 정책 연결, 오류 원인 보존.
5. 단위/HTTP/분리 DB 장애 테스트, OmniRoute 확인 가능한 범위 조사.
6. 실제 구현 기반 MD/PDF, 렌더·시각 검수, 최종 결과.

## 진행
- [x] 목표 등록, 기존 변경 목록 확인, 구조 조사.
- [x] 1 공통 오류 정책·단계별 핸들러·V14.
- [x] 2 카카오 원자 분할·작업 재개 및 네이버 매칭 보강.
- [x] 3 AI manifest·공급자·lease·장소별 저장·재수집.
- [x] 4 네이버 단건 복구·재시도 연결.
- [x] 5 전체 117건: 116 통과, live 1 생략, 실패 0. 전용 PostgreSQL 사용.
- [x] 6 GUIDE.md 및 19페이지 PDF 렌더 검수 완료.

## 외부 확인 및 제한
- 문서상 cooldown과 실제 quota reset은 다름. 실제 응답 근거가 있을 때만 reset 사용.
- OmniRoute 프로세스 재시작 복구는 별도 확인 필요.
- 검증은 별도 DB/모의 HTTP를 사용. 운영 DB에서 테스트하지 않음.

## 구현 진행 (검증 전)
- 공통 오류 정책/분류, next_retry_at, 공급자·모델/수집 lease/제출 manifest V14 추가.
- 카카오 전체 root·자식 원자 저장, 구역 단위 재개, 페이지 upsert 직렬화.
- 네이버 handler 및 주소만 같은 타 매장 선택 제거, 재시도 예약 연결.
- AI 부분 결과/장소별 저장 분리, manifest 완료 기록과 Place 저장의 동일 트랜잭션, 공급자별 poll.
- 기존 제출분의 provider/manifest는 추측해 채우지 않으며 운영 대조 필요.
- 현재 compileJava 단계 통과. 기존 테스트 생성자 수정 및 DB 장애 검증 진행 중.

## 완료 결과
- PDF: /Users/youjunsang/Documents/ChatGPT/Course/output/pdf/place-collection-guide.pdf
- 가이드·변경 보고서·렌더러·검증 요약·외부 확인 기록: docs/place-collection/
- 운영 DB와 서비스 변경 없음. V13/V14 운영 반영은 배포 시 필요.
- OmniRoute 3.8.53 소스 확인만 수행; 실제 quota 전달·worker 재시작은 미검증.
- 임시 테스트 DB 컨테이너는 검증 후 제거.
