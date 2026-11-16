# OmniRoute 확인 기록 · 2026-11-13

## 직접 확인
- 홈서버 설치 package.json 버전: 3.8.53.
- 설치 소스 open-sse/services/codexUsageQuotas.ts는 quota window의 resetAt 필드를 표현한다.
- 설치 소스 src/lib/db/batches.ts의 getPendingBatches는 validating/in_progress/finalizing/cancelling 레코드를 DB에서 읽는다.
- src/lib/batches/dispatch.ts는 배치 요청을 HTTP Response로 dispatch한다.
- 설정·계정·API 키 파일은 이 조사에서 읽거나 수정하지 않았다. 설치 소스와 버전만 조회했다.

## 근거가 부족한 부분
- 배치 결과 JSONL에 상위 공급자의 resets_at/resets_in_seconds가 항상 보존되는지 실시간 응답 검증하지 않았다.
- DB에 활성 작업이 남는다는 사실만으로 OmniRoute 프로세스 재시작 후 worker가 안전하게 재개한다고 확정할 수 없다.
- 다른 프로젝트가 사용 중이라 실제 OmniRoute 프로세스 종료·재시작 실험은 하지 않았다.
- 실제 토큰 소진을 유도하거나 새 모델 요청을 보내지 않았다.

## 구현에 반영한 한계
- 응답에 명시적인 resets_at/resets_in_seconds가 있으면 그 시각을 사용한다.
- 없다면 24시간 fallback을 사용한다.
- Retry-After와 rateLimitedUntil을 실제 quota 초기화 시각으로 추정하지 않는다.
- 원격이 실행 중이면 계속 조회하며 자동 취소·중복 제출하지 않는다.

공개 참고: https://github.com/cheaper-inference/OmniRoute
공개 문서: https://raw.githubusercontent.com/diegosouzapw/OmniRoute/main/docs/architecture/RESILIENCE_GUIDE.md
