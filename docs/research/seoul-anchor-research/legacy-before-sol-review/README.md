# 서울 AreaTag 앵커 조사

- 기준일: 2026-11-05
- 입력: /Users/youjunsang/Programming/project/course/course_backend/src/main/resources/db/seed/city/seoul
- 출력: /Users/youjunsang/Documents/Course/outputs/seoul-anchor-research
- 입력 규모: 29 Area, 288 AreaTag 관계, 13개 태그
- 진행: 1/29 Area 완료, 나머지 PENDING
- 지역 순서: 입력 CSV 행 순서. 한 Area씩 검토하고 완료 시 저장한다.
- 검색 기능: 웹 검색과 검색 결과 원문 페이지 열기를 확인했다. 시작 검증에서는 서울숲 공식 페이지의 주소·운영 정보를 확인했고, 이번 Area도 운영기관·현장 방문 페이지를 열어 내용과 주소를 대조했다.
- 범위: 입력 seed·애플리케이션·DB·전역 설정은 수정하지 않았다. 조사 결과만 이 디렉터리에 저장한다.

## 입력 SHA-256

| 파일 | SHA-256 |
|---|---|
| seoul-service-areas.csv | f75f46869b8f42c911a4d3de090dec186092f760aad84df14b2f0da062b70974 |
| seoul-area-tags.csv | f5d3003de37506f7a570324d40ca958e5f3214c9cb9287ff1020fc1945784be8 |
| seoul-area-legal-dong-mappings.csv | aba521cf81dcabc10545c3f12703e4ef00591b352654d6d2397843db64c969f9 |

## 정책

앵커는 해당 태그를 즐기기 위해 방문할 목적과 한국인 이용 근거가 확인되는 장소·거리·권역·노선이다. 공식 자료는 주소와 운영을, 국내 방문 기록과 탐방 자료는 실제 방문 목적을 뒷받침한다. 존재·업종만으로 채택하지 않고, 종료된 행사·전시는 현재 이용 가능하다고 안내하지 않는다. 내부 시설은 부모 관계를 기록한다.

## 진행 현황

- 완료: 북한산·강북 (8개 태그 검토, 12개 채택 관계, 15개 후보 검토)
- 다음: 도봉·노원
- 재개 기준은 progress.csv이며 완료 보고서는 areas/SEOUL_BUKHANSAN_GANGBUK.md이다.
- 관계별 출처는 sources.csv의 source_id와 evidence_ids로 연결한다.

## 현재 요약 및 한계

현재까지 12개 앵커 관계를 채택했다. 장미원골목시장은 독립 방문 수요 근거가 약해 보류했다. 수유시장 방문 근거는 2021년 시민기자 현장 기록이며 최근 방문객 규모를 뜻하지 않는다. 운영·공연·전시 일정은 변동되므로 방문 전에 공식 안내를 다시 확인해야 한다. 확인되지 않은 출처 게시일은 비워 둔다.

태그 재검토 제안은 없다. 기존 AreaTag를 변경하지 않았다.
