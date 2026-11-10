# 서울 Area 태깅 리서치

지역별 태그·가중치·방문 후보를 표로 보려면 **[서울 지역별 모아보기](SEOUL-AREA-OVERVIEW.md)**를 확인한다.

개발 참고용 **[288개 방문 매력·차별성 키워드](../seoul-area-ranking-pilot/KEYWORDS.md)**도 정리했다. 기존 근거를 요약한 자료이며, 미산정 순위와 근거 부족 판정을 구분한다.

태깅 후 별도 비교한 **[태그별 랭킹·진행표](../seoul-area-ranking-pilot/README.md)**도 확인할 수 있다. 검토용이며 기존 가중치나 최종 추천 정책을 변경한 결과는 아니다.

조사: 2026-11-02~03 (Asia/Seoul). 상태: 지역 태깅·교차 검토·방문 후보 목록 작성 완료.

방문 후보 결과: **29/29 Area · 308개 후보(개별 시설252 · 거리19 · 상권37)**. 기존 지역 보고서의 원문 확인·운영 제한을 재사용했으며 상세 주소 미확인은 그대로 표시했다. checked_at은 자료 검토일이고 방문 실사일이 아니다. [후보 CSV](area-visit-candidates.csv)와 각 지역 보고서 하단에서 확인할 수 있다.

최종 결과: **29/29 Area · 406개 판정 · 288개 부여 · 118개 근거 부족**. EXCLUDE는0개다. 자료가 없는 항목을 부재로 단정하지 않았다.
원본 목록·14개 판정·코드·중복·가중치·보고서 판정표·ASSIGN CSV 일치를 검증했다. 근거·가중치 기준의 교차 검토와 정정은 [최종 검토](REVIEW.md)에 기록했다.

현재 추가 정정: [카페·쇼핑·시장 기준 안정화 검토](../seoul-area-ranking-pilot/CRITERIA-STABILITY.md)에서 쇼핑 보류 2건을 80으로 부여했다. 현재 태그 288개·보류 118개·후보 308개다. 이전 검토 문서의 286개·306개는 해당 시점 기록이다.

## 최종 목표와 조사 기준

### 사용자 추가 범위: 방문 후보 목록 (2026-11-03)

기존 태깅 자료에서 방문 후보를 추출하고 아래 추가 범위를 수행했다. 원본 태그 판정과 별개로 후보를 보관하며, 완료 검증 결과는 REVIEW.md에 기록했다.

- 서울29개 Area의 기존 보고서·태그 근거에서 실제로 방문할 만한 개별 장소·거리·상권을 추출하고, 주소·소속 Area·현재 운영 단서를 재확인한다. 부족한 내용만 추가 검색한다.
- `area-visit-candidates.csv`를 작성한다. 헤더는 `city_code,area_code,candidate_code,name,kind,related_tags,address_or_extent,visit_reason,source_urls,checked_at,notes`로 한다. kind는 PLACE/STREET/DISTRICT, 복수 관련 태그와 출처는 `|`로 구분한다. candidate_code는 파일 내 고유한 검토용 식별자이며 DB Place ID가 아니다.
- 지역 태그의 부여 여부와 후보 보관 여부를 분리한다. 좋은 카페 한 곳이 있어도 지역 CAFE 근거가 부족하면 후보만 보관할 수 있다. 후보의 related_tags는 지역 태그를 자동 부여하는 값이 아니다.
- 대표 명소만 강제하지 않으며 지역별 최소 후보 수를 두지 않는다. 개별 장소는 향후 앵커 검토 후보, 거리·상권은 주변 장소 탐색 범위로 구분한다. 이 파일을 그대로 확정 앵커 목록이라고 부르지 않는다.
- 용산 아이파크몰 같은 뚜렷한 방문 목적지가 지역 태그 판정에서 누락됐는지 양방향 점검한다. 태그가 높다면 이를 설명하는 구체적 방문 후보·거리 근거가 있는지도 확인한다. 후보 하나의 존재만으로 지역 태그를 자동 부여하지 않는다.
- 중복·이전·폐업·상시 개방과 예약·기간 행사·Area 경계 및 원거리 하위 상권을 점검하고, 확인되지 않은 주소·운영 정보는 추정하지 않는다. 후보의 관련 태그는 기존14개 조사 코드만 사용한다.
- 기존 Area/태그 코드 유효성, 후보 코드·장소 중복, 출처·확인일·방문 이유, PLACE와 거리·상권의 구분, 후보↔지역 태그의 정합성을 검증하고 README·지역 보고서·최종 검토를 갱신한다.

이번 추가 작업도 문서·CSV 범위다. 코드·migration·DB 적재·앵커 확정·태그별 랭킹은 포함하지 않는다. 이 절은 사용자가 승인한 추가 작업 명세이며 아래 산출물까지 함께 관리한다.

서울 서비스 Area 전체를 개별 웹 리서치하여 방문 목적 태그와 가중치를 결정한다. 전체 지역 태깅 후 태그별 순위는 별도 작업으로 산정한다. 코드·migration·DB는 수정하지 않는다.

진실 원천은 db/seed/city/seoul/seoul-service-areas.csv, seoul-area-legal-dong-mappings.csv, db/seed/tags.csv이다. 실제 Area는 29개다. 법정동 매핑에는 REVIEW_REQUIRED 표시가 남아 있어 확정된 공식 생활권으로 주장하지 않는다.

## 14개 태그 정의

|코드|방문 목적 판정 기준|
|---|---|
|CAFE|카페 탐방 또는 카페거리. 일반 카페의 존재만으로 부여하지 않음|
|SHOPPING|브랜드·편집숍·빈티지숍·전문 상가에서 상품을 탐색하는 목적. 몰·아울렛 포함; 시장 먹거리·장보기만으로 부여하지 않음|
|POPUP|반복적으로 서로 다른 팝업이 열리는 상권. 단발 행사만으로 부여하지 않음|
|EXHIBITION|미술관·박물관·갤러리 목적. 판매 팝업과 구분|
|MARKET|전통시장·벼룩시장·전문시장의 골목·좌판·먹거리·특화품목 구경. 플리마켓은 현재 반복 운영 확인 필요|
|WALK|무리한 등산 없이 걸으며 구경할 구체적인 경로|
|PARK|도시 공원 산책·휴식·피크닉. 산악 국립공원 존재와 구분|
|PERFORMANCE|공연장·라이브클럽·반복 거리공연. 공연 유형 기록|
|BOOKSTORE|대표 서점 방문·서점 탐방. 도서관·북카페와 구분|
|WORKSHOP|방문자가 체험할 수 있는 공방. 제품 판매·주민 장기강좌와 구분|
|TRENDY|새로운 브랜드·문화·공간의 집적이 지역 특성으로 확인됨|
|LIVELY|주요 방문 상권의 활기. 특정 행사·매장의 혼잡만으로 부여하지 않음|
|QUIET|주요 방문 경로의 차분함. 일부 카페를 넓은 지역으로 일반화하지 않음|
|SCENIC_VIEW|전망·야경·수변·자연 경관 감상이 방문 목적|

쇼핑·시장은 별개 방문 목적이 각각 확인되면 같은 지역·시설에 함께 부여할 수 있다. 29개 지역·58개 판정을 재검토한 [쇼핑·시장 구분 기준과 결과](SHOPPING-MARKET-REVIEW.md)를 따른다. 부여·가중치는 유지하고 근거와 후보 설명을 분리했으며, 랭킹은 태그별로 비교한다.

## 판정과 가중치

- ASSIGN: 방문 목적 근거 충분. 100=핵심 대표 목적, 90=대표 목적 중 하나, 80=뚜렷한 방문 목적, 70=의미 있는 부가·연계 목적.
- EXCLUDE: 검토한 근거가 정의를 충족하지 않음. 검색되지 않았다는 이유로 제외하지 않는다.
- INSUFFICIENT_EVIDENCE: 근거 부족·시간경과·충돌로 보류. 가중치는 비운다.
- 가중치는 다른 지역 대비 순위나 인지도가 아니다. 출처가 확실하더라도 부가 목적이면 70일 수 있다.
- 인지도 근거는 문서에 남기되 숫자로 만들지 않는다. 이용 인기도는 기존 AreaStats의 별개 의미다.

## 수행 절차

1. Area의 법정동·주요 상권·인접 경계를 확인한다.
2. 지역·동·거리·역 이름을 검색하고 서울 카페거리·쇼핑거리·전시·서점·공방·산책 검색과 교차한다.
3. 공식 관광·지자체·시설 원문과 독립적인 방문 후기를 검토한다. 검색 요약만 확인한 출처는 명시하고 중심 근거로 사용하지 않는다.
4. 복제·재배포·동일 작성자의 글을 독립 근거로 중복 계산하지 않는다. 내돈내산 주장 자체로 신뢰성을 확정하지 않는다.
5. 모든 Area의 14개 태그를 판정하고 이유와 근거 상권을 기록한다. 태그 수를 강제로 맞추지 않는다.
6. 부여는 지역 단위 자료와 독립 보강 근거를 원칙으로 한다. 공원·미술관 등 시설 자체가 방문 목적이면 공식 위치·운영과 목적 근거로 판정하고 단일 목적지임을 명시한다.
7. 게시·수정일과 확인일을 구분한다. 접근 가능하다는 이유만으로 현재 영업을 확정하지 않는다.
8. 서로 먼 상권에서 나온 태그를 가까운 동선으로 조합 가능하다고 가정하지 않는다. 조건부 분위기는 주요 방문권·시간을 명시한다. 일상적 분위기 원문이 있는 주요 상권에 한정할 수 있지만 단일 행사·매장·조용한 카페로 확대하지 않는다.
9. 각 Area 완료 즉시 보고서·CSV·진행표를 저장한다. 기준 변경 시 이전 지역도 재검토한다.

## 산출물과 완료 검토

- areas/{area_code}.md: 경계·검색어·출처와 날짜/원문 확인 상태·판정·인지도 근거·주의점.
- area-tag-decisions.csv: 조사한 Area별 14개 판정, 가중치, 근거와 출처. 조사 전 지역을 보류로 미리 채우지 않는다.
- area-tags.csv: ASSIGN 연결만 수록. 검토용이며 이번 작업에서 DB 적재하지 않는다.
- area-visit-candidates.csv: 29개 지역의 방문 후보308개. PLACE252개는 독립 방문 시설·시설 내 목적 공간, STREET19개는 거리·경로, DISTRICT37개는 상권·복수 시설 묶음이다. parent와 child 공간이 함께 있어308개를 서로 겹치지 않는 DB Place 수로 해석하지 않는다.
- 29개 지역/406개 판정, 태그·Area 코드, 중복, ASSIGN 일치, 가중치·이유와 보고서 판정표 일치를 검증했다. 경계 혼입·원문 실패·시간경과·홍보성의 교차 검토는 REVIEW.md를 따른다.
- 보류 판정의 source_urls가 비어 있으면 채택할 구체적 출처를 확보하지 못한 경우다. 임의의 검색 URL로 채우지 않는다.

## 지역별 결과

|지역 보고서|Area 코드|부여|근거 부족|
|---|---|---:|---:|
|[북한산·강북](areas/SEOUL_BUKHANSAN_GANGBUK.md)|SEOUL_BUKHANSAN_GANGBUK|8|6|
|[도봉·노원](areas/SEOUL_DOBONG_NOWON.md)|SEOUL_DOBONG_NOWON|9|5|
|[은평·서대문](areas/SEOUL_EUNPYEONG_YEONHUI.md)|SEOUL_EUNPYEONG_YEONHUI|9|5|
|[신촌·이대](areas/SEOUL_SINCHON_EWHA.md)|SEOUL_SINCHON_EWHA|8|6|
|[광화문·서촌](areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|SEOUL_GWANGHWAMUN_SEOCHON|11|3|
|[북촌·인사동·종로](areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|SEOUL_BUKCHON_INSADONG_IKSUN|12|2|
|[대학로·동대문](areas/SEOUL_DAEHAKRO_DONGDAEMUN.md)|SEOUL_DAEHAKRO_DONGDAEMUN|9|5|
|[명동·을지로](areas/SEOUL_MYEONGDONG_EULJIRO.md)|SEOUL_MYEONGDONG_EULJIRO|11|3|
|[서울역·남대문](areas/SEOUL_SEOUL_STATION_NAMDAEMUN.md)|SEOUL_SEOUL_STATION_NAMDAEMUN|9|5|
|[홍대·연남](areas/SEOUL_HONGDAE_YEONNAM.md)|SEOUL_HONGDAE_YEONNAM|10|4|
|[상수·합정·망원](areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|SEOUL_SANGSU_HAPJEONG_MANGWON|11|3|
|[마포](areas/SEOUL_MAPO_GONGDEOK.md)|SEOUL_MAPO_GONGDEOK|8|6|
|[성수](areas/SEOUL_SEONGSU.md)|SEOUL_SEONGSU|11|3|
|[왕십리·건대](areas/SEOUL_WANGSIMNI_KONKUK.md)|SEOUL_WANGSIMNI_KONKUK|10|4|
|[청량리](areas/SEOUL_CHEONGNYANGNI_JUNGNANG.md)|SEOUL_CHEONGNYANGNI_JUNGNANG|8|6|
|[용산](areas/SEOUL_YONGSAN.md)|SEOUL_YONGSAN|13|1|
|[이태원·한남](areas/SEOUL_ITAEWON_HANNAM.md)|SEOUL_ITAEWON_HANNAM|8|6|
|[여의도](areas/SEOUL_YEOUIDO.md)|SEOUL_YEOUIDO|10|4|
|[영등포·문래](areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|SEOUL_YEONGDEUNGPO_MULLAE|12|2|
|[목동·양천](areas/SEOUL_MOKDONG_YANGCHEON.md)|SEOUL_MOKDONG_YANGCHEON|9|5|
|[강서·김포](areas/SEOUL_GANGSEO_MAGOK.md)|SEOUL_GANGSEO_MAGOK|11|3|
|[구로·금천](areas/SEOUL_GURO_GEUMCHEON.md)|SEOUL_GURO_GEUMCHEON|9|5|
|[관악·사당](areas/SEOUL_GWANAK_SADANG.md)|SEOUL_GWANAK_SADANG|12|2|
|[서초·반포](areas/SEOUL_SEOCHO_BANPO.md)|SEOUL_SEOCHO_BANPO|11|3|
|[강남·역삼](areas/SEOUL_GANGNAM_YEOKSAM.md)|SEOUL_GANGNAM_YEOKSAM|8|6|
|[압구정·청담](areas/SEOUL_APGUJEONG_CHEONGDAM.md)|SEOUL_APGUJEONG_CHEONGDAM|10|4|
|[삼성·코엑스](areas/SEOUL_SAMSUNG_COEX.md)|SEOUL_SAMSUNG_COEX|9|5|
|[잠실·송파](areas/SEOUL_JAMSIL_SONGPA.md)|SEOUL_JAMSIL_SONGPA|12|2|
|[강동](areas/SEOUL_GANGDONG.md)|SEOUL_GANGDONG|10|4|

## 읽는 순서와 후속 작업

1. 지역별 보고서에서 근거 방문권과 운영·시간·경계 제한을 확인한다.
2. area-tag-decisions.csv에서 부여·보류 이유를 검토하고, area-tags.csv를 적재 후보로 사용한다. area-visit-candidates.csv에서 실제 방문 근거와 장소 후보를 확인한다.
3. 모든 지역 태깅이 끝난 이 자료를 바탕으로 **태그별 보유 지역의 경쟁력 순위는 별도로 산정**한다. 이번 가중치를 서울 내 순위로 해석하지 않는다.

29개 Area는 세부 동네보다 넓으므로 CAFE·공연·산책이 여러 지역에 존재할 수 있다. 태그 수를 목표로 채운 결과가 아니며, 서로 먼 목적지의 태그를 한 코스에서 모두 충족한다고 가정하면 안 된다. QUIET은 주요 방문권을 설명할 충분한 근거를 확보하지 못해 전 지역 보류했다. 조용한 장소가 없다는 뜻은 아니다.

이 산출물은 확인일 기준의 근거 있는 방문 목적 편집안이다. 전수 점포 조사나 실시간 영업 보증은 아니다. 팝업 종료·전시 교체·공원 공사·예약 조건은 실제 추천 시점에 확인해야 한다. 코드·migration·DB는 수정하지 않았다.

조사 과정의 중간 메모는 [체크포인트 이력](CHECKPOINT-NOTES.md)에 보관했다. 중간의 대기·재확인 문구보다 현재 README·판정 CSV·최종 검토를 우선한다.

## 방문 후보 사용법

- 이름 검색·수집의 출발점으로 사용한다. candidate_code는 검토용 ID이며 카카오·네이버·DB Place ID가 아니다.
- address_or_extent는 확인한 범위만 기록했다. 구·동·거리만 확인된 항목과 상세 도로명이 있는 항목이 섞여 있으므로 자동 지오코딩 입력 전에 notes를 확인한다. 새 좌표를 추정하지 않았다.
- 시설의 존재·과거 방문 기록과 현재 즉시 입장·예약 가능 여부는 다르다. 휴관·이전·품절·예약·현재성 미확인 후보는 notes의 제한을 따른다. 보류 후보는 바로 사용자에게 추천할 확정 장소가 아니다.
- 상권과 내부 시설, 복합몰과 내부 서점 등은 용도가 달라 함께 남겼다. 같은 장소를 중복 수집·추천하지 않도록 실제 Place 연결 시 공간 관계를 구분해야 한다.
- 지역 태그가 보류된13개 Area·태그 조합에도 관련 후보가 남아 있다. 이는 태그 자동 부여나 부재 판정이 아니라 추가 확인할 근거를 보존한 것이다.
