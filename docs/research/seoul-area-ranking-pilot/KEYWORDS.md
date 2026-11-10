# 서울 Area 방문 매력·차별성 키워드

정리일: 2026-11-03. 기존 조사에서 태그가 부여된 **288개 지역–태그 연결 전체**를 정리했다. 기존 80개를 유지하고 나머지 208개를 추가했다. 부여 태그는 13종이며 QUIET은 전 지역 근거 부족으로 보류 상태다.

최초 키워드 정리는 기존 근거의 요약이었다. 이후 태그별로 원문을 대조하며 랭킹과 키워드를 함께 확정했다. 현재 **13개 태그·288개 순위 완료**, 미산정 대상은 없다. 완료 태그와 재검토 범위는 [진행표](PROGRESS.md)의 결정 기록을 따른다. 기존 weight나 키워드 개수로 정렬하지 않았다.

## 원본과 필드 의미

통합 원본은 [키워드 CSV](area-tag-keywords.csv)다. `(city_code, area_code, tag_code)`로 [지역 태그 판정](../seoul-area-tagging/area-tag-decisions.csv)과 [순위 CSV](area-tag-rankings-pilot.csv)를 연결한다. 완료 태그의 순위 CSV 키워드 열은 통합 원본과 일치한다. 이후 키워드 수정 시 통합 원본을 기준으로 표와 해당 순위의 키워드도 함께 갱신한다.

|필드|의미|
|---|---|
|visit_scope|실제로 특징을 확인한 방문권. 넓은 Area 전체나 모든 매장에 적용하지 않는다.|
|attraction_keywords|그 태그를 목적으로 찾아갈 이유. 예: 재생공간 카페 탐방, 꽃·원예 재료 구경.|
|differentiation_keywords|경험의 구체적인 형태·특화 요소·근거 시설. 예: 창고·공장 재생, 엽전 도시락. 다른 모든 지역에 없는 유일한 특징이라는 뜻은 아니다.|
|source_urls|기존 조사·순위 판단에서 채택한 출처. 여러 주소는 세로줄로 구분한다.|
|checked_at|태그별 비교 근거를 검토한 날짜. 원문의 게시일·촬영일·현장 방문일·당일 영업 인증일이 아니다.|
|keywords_updated_at|키워드를 정리한 날짜. 현장 방문일이나 영업 재확인일이 아니다.|

키워드 목록은 세미콜론(`;`)으로 나누고 앞뒤 공백을 제거해 배열로 읽을 수 있다. 모든 ASSIGN 연결을 기록하되 키워드 수를 강제하지 않는다. 근거 부족인 118개 연결은 원래 판정 파일에서 관리하고 긍정 키워드를 임의로 채우지 않았다.

## 개발 시 해석

추천 이유·지역 소개·후보 성격 설명에 활용한다. 예: 성수의 CAFE 키워드로 “창고를 재해석한 공간을 찾아 카페를 둘러볼 수 있는 지역”처럼 설명할 수 있다. 모든 성수 카페의 속성을 뜻하지 않는다.

한 Area가 넓어 여러 동네를 포함하므로 태그마다 방문권이 다를 수 있다. 서로 다른 태그의 키워드를 합쳤다고 가까운 코스로 조합할 수 있는 것은 아니다. 가격·조용함·이동 시간 등 기록하지 않은 속성은 추정하지 않는다. 키워드가 많다는 이유로 점수를 더하지 않으며 일반 시설명 자체가 높은 경쟁력의 증거도 아니다.

예약·입장·휴관·기간·주민 통행 제한은 [지역별 보고서](../seoul-area-tagging/README.md)와 [방문 후보의 notes](../seoul-area-tagging/area-visit-candidates.csv)를 함께 확인한다. 예를 들어 한국영화박물관의 조사 당시 임시휴관, DDP 루프탑 투어의 운영 조건, 관악아트홀 공연의 장소 변경은 키워드가 있다고 해제되지 않는다. 과거 팝업·공연·수업 편성은 현재 참여 가능함을 보증하지 않는다.

## 태그별 기록 상태

|태그|키워드 정리|순위 상태|방문 매력·차별성의 해석|
|---|---:|---|---|
|카페 (CAFE)|28|완료|공간·음료·디저트를 찾아가는 경험. 원두 맛 전수 심사와 구분한다.|
|쇼핑 (SHOPPING)|28|완료|브랜드·편집숍·몰·상품 전문 상가에서 상품을 고르는 경험. 시장 음식·정취와 구분한다.|
|시장 구경 (MARKET)|24|완료|골목·좌판·먹거리·특화 품목을 구경하는 경험. 종료된 축제를 상시 특징으로 쓰지 않는다.|
|팝업 (POPUP)|16|완료|반복 개최된 브랜드·테마의 방문 매력과 공간 형태. 과거 편성을 지금 열리는 행사로 표시하지 않는다.|
|전시 (EXHIBITION)|28|완료|작품·주제를 관람할 목적과 전시 공간의 성격. 판매 팝업·단순 건물 규모와 구분한다.|
|산책 (WALK)|29|완료|실제 걸으며 구경하는 경로와 연결 방식. 정상 등산·서로 먼 경로를 한 산책로로 묶지 않는다.|
|공원 (PARK)|28|완료|휴식·나들이·피크닉·자연 관찰의 구체적인 장소와 형태. 공원 자체와 전망의 매력을 구분한다.|
|공연 (PERFORMANCE)|29|완료|관람할 공연 유형·공연장·반복 편성. 공연장 존재가 현재 즉시 예매 가능함을 뜻하지 않는다.|
|서점 (BOOKSTORE)|23|완료|책을 고르고 탐색하는 경험과 서점 성격. 도서관·무료 서가만으로 판매 서점 태그를 만들지 않는다.|
|공방 (WORKSHOP)|14|완료|방문자가 직접 만드는 대상·방식·예약 조건. 주민 장기강좌나 제품 구매만으로 체험을 가정하지 않는다.|
|힙한 (TRENDY)|12|완료|브랜드·문화·공간이 모여 만드는 상권의 특징. 비싼 곳·새 건물·팝업 한 번만으로 일반화하지 않는다.|
|활기찬 (LIVELY)|7|완료|거리·상권의 방문·거래 활동과 관찰된 시간대. 일회성 행사나 단일 매장 대기를 지역 전체의 혼잡으로 확대하지 않는다.|
|조용한 (QUIET)|0|대상 없음·근거 부족|주요 방문 경로의 차분함을 설명할 근거가 필요하다. 현재는 29개 지역 모두 보류라 키워드·순위를 생성하지 않는다.|
|경관 (SCENIC_VIEW)|22|완료|경관을 감상할 대상과 조망 방식. 도심·수변·야경·조명 등 유형을 기록하고 공원 존재와 분리한다.|

각 태그의 방문 목적에 맞는 세부 기준·비교 이유·원문 확인 한계는 [진행표의 결정 기록](PROGRESS.md)을 따른다. 전체 288개 연결을 검토했으며 [최종 검증 기록](FINAL-REVIEW.md)에 범위와 결과를 남겼다. 키워드의 단순 합산·인기도 대체값으로 사용하지 않는다.

## 카페 (CAFE)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|연무장길|재생공간 카페 탐방 · 공간 선택|창고·공장 재생 · 연무장길|
|2|[홍대·연남](../seoul-area-tagging/areas/SEOUL_HONGDAE_YEONNAM.md)|연남 성미산로·숲길 골목|주택형 카페 탐방 · 디저트 탐방|주택형 공간 · 골목 카페권|
|3|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|익선동 한옥 카페 골목|한옥 카페 탐방 · 찻집 방문|한옥 공간 · 익선동 골목|
|4|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|망원·망리단길 카페권|독립 카페 탐방 · 골목 탐방|망원·망리단길 · 독립 매장|
|5|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|통인·누하 서촌 카페권|한옥·골목 카페 탐방|서촌 골목 · 통인·누하|
|6|[압구정·청담](../seoul-area-tagging/areas/SEOUL_APGUJEONG_CHEONGDAM.md)|도산·압구정 카페권|브랜드 카페 방문 · 공간형 카페 탐방|도산·압구정 · 브랜드 공간|
|7|[이태원·한남](../seoul-area-tagging/areas/SEOUL_ITAEWON_HANNAM.md)|한남·한강진 카페권|콘셉트 카페 탐방|한남·한강진 · 라이프스타일 공간|
|8|[은평·서대문](../seoul-area-tagging/areas/SEOUL_EUNPYEONG_YEONHUI.md)|연희동 카페권|동네 카페 탐방 · 공간 탐방|연희동 카페권|
|9|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|을지로2·3가 카페 골목|개성 있는 카페 방문 · 골목 탐방|커피한약방 · 을지로 골목|
|10|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|송리단길·석촌호수로 카페권|디저트 카페 탐방 · 카페거리 탐방|송리단길 · 석촌호수로|
|11|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|문래 재생 카페 골목|재생공간 카페 방문|산업 골목 재해석 · 러스트베이커리|
|12|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|용리단길 카페권|카페 탐방|용리단길|
|13|[북한산·강북](../seoul-area-tagging/areas/SEOUL_BUKHANSAN_GANGBUK.md)|수유 사일구로 카페거리|경관형 카페 체류|산 배경 · 사일구로 카페거리|
|14|[도봉·노원](../seoul-area-tagging/areas/SEOUL_DOBONG_NOWON.md)|공릉 경춘선숲길 카페권|카페 탐방 · 걸으며 매장 선택|옛 철길 · 경춘선숲길|
|15|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|반포 서래마을 카페권|카페·베이커리 탐방|서래마을|
|16|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|봉천 샤로수길 카페권|카페 선택 · 식사 연계 방문|샤로수길 음식 골목|
|17|[신촌·이대](../seoul-area-tagging/areas/SEOUL_SINCHON_EWHA.md)|창천·연세로 카페권|목적 카페 방문 · 대학가 카페 탐방|독수리다방 · 연세로|
|18|[대학로·동대문](../seoul-area-tagging/areas/SEOUL_DAEHAKRO_DONGDAEMUN.md)|혜화·대학로 카페권|목적 카페 방문 · 공연 전후 방문|학림 · 대학로|
|19|[마포](../seoul-area-tagging/areas/SEOUL_MAPO_GONGDEOK.md)|공덕·도화 카페 방문권|목적 카페 방문|프릳츠 · 도화 방문권|
|20|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|마곡·발산 카페권|공간형 카페 방문|마곡·발산 · 테라스형 공간|
|21|[청량리](../seoul-area-tagging/areas/SEOUL_CHEONGNYANGNI_JUNGNANG.md)|제기동 경동1960|재생공간 카페 방문|옛 극장 재생 · 경동1960 · 계단식 좌석|
|22|[구로·금천](../seoul-area-tagging/areas/SEOUL_GURO_GEUMCHEON.md)|가산 인크커피|대형 카페 체류 · 베이커리 방문|가산 인크커피 · 대형 공간|
|23|[서울역·남대문](../seoul-area-tagging/areas/SEOUL_SEOUL_STATION_NAMDAEMUN.md)|회현 피크닉 카페 방문권|문화공간 카페 방문|회현 피크닉 · 문화공간 결합|
|24|[강남·역삼](../seoul-area-tagging/areas/SEOUL_GANGNAM_YEOKSAM.md)|역삼 셀렉티드닉스|층별 공간 탐방|셀렉티드닉스 · 갤러리 · 루프톱|
|25|[목동·양천](../seoul-area-tagging/areas/SEOUL_MOKDONG_YANGCHEON.md)|현대 목동점 떼르드카페·보타닉하우스|정원형 카페 체류 · 전문 커피|보타닉하우스 · 떼르드카페|
|26|[삼성·코엑스](../seoul-area-tagging/areas/SEOUL_SAMSUNG_COEX.md)|포스코센터 테라로사|목적 카페 방문|포스코센터 · 테라로사|
|27|[왕십리·건대](../seoul-area-tagging/areas/SEOUL_WANGSIMNI_KONKUK.md)|자양 커먼그라운드 인덱스숍|북카페 방문|인덱스숍 · 선별 도서 결합|
|28|[여의도](../seoul-area-tagging/areas/SEOUL_YEOUIDO.md)|더현대 서울 내 카멜커피|목적 카페 방문 · 몰 연계 방문|더현대 서울 · 카멜커피|

## 쇼핑 (SHOPPING)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|명동 중심 쇼핑권|다분야 상품 선택 · 브랜드 매장 탐방|명동 브랜드 거리|
|2|[홍대·연남](../seoul-area-tagging/areas/SEOUL_HONGDAE_YEONNAM.md)|홍대역 서교 상품 매장권|패션·뷰티 쇼핑 · 캐릭터 상품 탐방|홍대역 서교 매장권 · 상품 분야 결합|
|3|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|연무장길 상설 브랜드권|상설 브랜드 탐방 · 편집숍 쇼핑|플래그십 · 연무장길|
|4|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|잠실 몰·백화점 방문권|다분야 상품 선택 · 복합 쇼핑|잠실 몰·백화점 방문권|
|5|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|반포 신세계·고투몰 방문권|가격대별 상품 선택 · 복합 쇼핑|신세계·고투몰 · 연결 방문권|
|6|[여의도](../seoul-area-tagging/areas/SEOUL_YEOUIDO.md)|더현대 서울|브랜드 상품 탐색 · 몰 쇼핑|더현대 서울|
|7|[압구정·청담](../seoul-area-tagging/areas/SEOUL_APGUJEONG_CHEONGDAM.md)|도산·압구정 브랜드권|디자이너 패션 탐방|도산·압구정 · 브랜드 매장권|
|8|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|용산역 아이파크몰|패션·리빙·취미 상품 선택|용산역 아이파크몰|
|8|[삼성·코엑스](../seoul-area-tagging/areas/SEOUL_SAMSUNG_COEX.md)|스타필드 코엑스몰|다분야 상품 선택 · 몰 쇼핑|스타필드 코엑스몰|
|10|[서울역·남대문](../seoul-area-tagging/areas/SEOUL_SEOUL_STATION_NAMDAEMUN.md)|남대문 품목별 상품 상가|품목별 상품 비교|아동복 · 잡화 · 주방용품 · 품목별 상가|
|11|[구로·금천](../seoul-area-tagging/areas/SEOUL_GURO_GEUMCHEON.md)|가산 마리오아울렛|여러 브랜드 비교 · 아울렛 쇼핑|가산 마리오아울렛|
|12|[이태원·한남](../seoul-area-tagging/areas/SEOUL_ITAEWON_HANNAM.md)|한남 쇼룸·플래그십권|디자이너 상품 탐방 · 라이프스타일 쇼핑|한남 쇼룸·플래그십 · 향·생활용품|
|13|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|영등포 타임스퀘어|다분야 상품 선택 · 복합 쇼핑|영등포 타임스퀘어|
|14|[강남·역삼](../seoul-area-tagging/areas/SEOUL_GANGNAM_YEOKSAM.md)|강남대로 역삼측 상품 매장권|브랜드 목적 매장 방문|강남대로 역삼측 · Apple · Nike|
|15|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|인사동 공예·기념품 상점권|공예·기념품 쇼핑|인사동 · 공예·골동품 상점|
|16|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|망리단길 소품숍권|소품숍 탐방 · 독립 매장 쇼핑|망리단길 소품숍권|
|17|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|서촌 편집숍 골목|디자인·소품 쇼핑|서촌 편집숍 골목|
|18|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|롯데몰 김포공항점|패션·생활 상품 선택 · 몰 쇼핑|롯데몰 김포공항점|
|19|[도봉·노원](../seoul-area-tagging/areas/SEOUL_DOBONG_NOWON.md)|상계 롯데백화점 노원점|패션·뷰티 상품 선택 · 백화점 쇼핑|상계 롯데백화점 노원점|
|20|[강동](../seoul-area-tagging/areas/SEOUL_GANGDONG.md)|천호 백화점·상품 매장권|상품 매장 탐방 · 백화점 쇼핑|천호 백화점·거리 매장권|
|21|[청량리](../seoul-area-tagging/areas/SEOUL_CHEONGNYANGNI_JUNGNANG.md)|청량리역 쇼핑시설|다분야 상품 선택|청량리역 쇼핑시설|
|22|[목동·양천](../seoul-area-tagging/areas/SEOUL_MOKDONG_YANGCHEON.md)|현대백화점 목동점|다분야 상품 선택 · 백화점 쇼핑|현대백화점 목동점|
|23|[북한산·강북](../seoul-area-tagging/areas/SEOUL_BUKHANSAN_GANGBUK.md)|길음 현대백화점 미아점|패션·뷰티·생활용품 선택|길음 현대백화점 미아점|
|24|[신촌·이대](../seoul-area-tagging/areas/SEOUL_SINCHON_EWHA.md)|창천 유플렉스|브랜드·패션 상품 탐색|창천 유플렉스|
|25|[대학로·동대문](../seoul-area-tagging/areas/SEOUL_DAEHAKRO_DONGDAEMUN.md)|창신 완구거리·주변 고정 빈티지숍|완구·빈티지 상품 탐방|창신 완구거리 · 고정 빈티지숍|
|26|[왕십리·건대](../seoul-area-tagging/areas/SEOUL_WANGSIMNI_KONKUK.md)|자양 커먼그라운드|패션·소품 매장 탐방|자양 커먼그라운드|
|27|[은평·서대문](../seoul-area-tagging/areas/SEOUL_EUNPYEONG_YEONHUI.md)|연희 문구·소품 상점권|문구·엽서·소품 쇼핑|연희 문구·소품 상점권|
|28|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|신림 타임스트림|여러 브랜드 쇼핑|신림 타임스트림|

## 시장 구경 (MARKET)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[서울역·남대문](../seoul-area-tagging/areas/SEOUL_SEOUL_STATION_NAMDAEMUN.md)|남대문시장|종합시장 구경 · 시장 먹거리|품목별 골목 · 다양한 점포|
|2|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|광장시장|시장 먹거리 · 전통시장 골목 구경|광장시장 음식 탐방|
|3|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|망원시장|시장 먹거리 · 생활 점포 구경|망원시장 먹거리·생활 점포 결합|
|4|[대학로·동대문](../seoul-area-tagging/areas/SEOUL_DAEHAKRO_DONGDAEMUN.md)|동묘 벼룩시장|중고품 탐색 · 좌판 구경|동묘 벼룩시장 · 중고 좌판|
|5|[청량리](../seoul-area-tagging/areas/SEOUL_CHEONGNYANGNI_JUNGNANG.md)|경동시장|식재료·약재 구경|경동시장 품목 탐방|
|6|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|노량진수산시장|수산물 선택 · 현장 식사|수산 전문시장 · 점포 탐색·식사 결합|
|7|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|통인시장|먹거리 선택 · 도시락 체험|엽전 도시락|
|8|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|양재꽃시장|꽃·원예 재료 구경|생화·분화·자재 · 꽃 전문시장|
|9|[왕십리·건대](../seoul-area-tagging/areas/SEOUL_WANGSIMNI_KONKUK.md)|마장동 축산시장|축산물 선택 · 전문시장 구경|축산물 전문 점포|
|10|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|서울중앙시장|식재료·먹거리 탐방 · 점포 구경|서울중앙시장 식재료·먹거리 결합|
|11|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|대림중앙시장|중국 먹거리 탐방 · 식재료 구경|대림중앙시장 중국 먹거리·식재료|
|12|[은평·서대문](../seoul-area-tagging/areas/SEOUL_EUNPYEONG_YEONHUI.md)|연서시장|골목 구경 · 시장 먹거리|미로형 골목 · 바 형태 먹자골목|
|13|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|까치산시장|시장 간식 · 생활 점포 구경|까치산시장 간식·생활 점포 결합|
|14|[압구정·청담](../seoul-area-tagging/areas/SEOUL_APGUJEONG_CHEONGDAM.md)|논현 영동전통시장|음식 탐방 · 만남 · 시장 문화|논현 영동전통시장 음식·만남 문화|
|15|[강동](../seoul-area-tagging/areas/SEOUL_GANGDONG.md)|암사종합시장|시장 간식 · 점포 구경|암사종합시장 아케이드|
|16|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|새마을시장|시장 먹거리 · 생활 점포 구경|새마을시장 주거형 시장 경험|
|17|[도봉·노원](../seoul-area-tagging/areas/SEOUL_DOBONG_NOWON.md)|공릉 도깨비시장|시장 음식 · 점포 구경|공릉 도깨비시장 방문|
|18|[북한산·강북](../seoul-area-tagging/areas/SEOUL_BUKHANSAN_GANGBUK.md)|수유시장|시장 먹거리 · 점포 구경|수유시장 방문|
|19|[목동·양천](../seoul-area-tagging/areas/SEOUL_MOKDONG_YANGCHEON.md)|목동깨비시장|시장 먹거리 · 생활 점포 구경|도깨비 조명 · 입구 포토존|
|20|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|용문시장|시장 음식 · 생활 점포 구경|용문시장 생활시장 경험|
|20|[구로·금천](../seoul-area-tagging/areas/SEOUL_GURO_GEUMCHEON.md)|별빛남문시장|음식점·시장 통로 구경|별빛남문시장 · 중국식 음식점|
|22|[마포](../seoul-area-tagging/areas/SEOUL_MAPO_GONGDEOK.md)|아현시장|음식 목적지 방문|아현시장 음식 방문|
|23|[삼성·코엑스](../seoul-area-tagging/areas/SEOUL_SAMSUNG_COEX.md)|대치 은마종합상가|먹거리 · 장보기|은마종합상가 시장형 상가|
|24|[강남·역삼](../seoul-area-tagging/areas/SEOUL_GANGNAM_YEOKSAM.md)|역삼 도곡시장|생활 식품 · 먹거리 골목 방문|도곡시장 생활 식품 골목|

## 팝업 (POPUP)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|연무장길·성수역 팝업 방문권|브랜드 체험 탐방 · 콘셉트 공간 탐방|공간 실험 · 패션·게임·식음 테마 · 거리형 탐방|
|2|[여의도](../seoul-area-tagging/areas/SEOUL_YEOUIDO.md)|더현대 서울|브랜드·캐릭터 팝업 탐방|주간 기획 · 몰 내 테마 교체 · 실내 방문권|
|3|[홍대·연남](../seoul-area-tagging/areas/SEOUL_HONGDAE_YEONNAM.md)|동교 무신사·AK홍대 팝업 방문권|패션·서브컬처 팝업 탐방|패션 컬렉션 · 팬덤 콘텐츠 · 동교 방문권|
|4|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|롯데월드몰 행사장|게임 IP 체험 · 식음 팝업 탐방|게임·음악 체험존 · 아트리움 · 테마형 공간|
|5|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|아이파크몰 도파민 스테이션|취향형 팝업 탐방 · 브랜드 체험|팬덤·취미 콘텐츠 · 전용 콘텐츠 공간|
|6|[압구정·청담](../seoul-area-tagging/areas/SEOUL_APGUJEONG_CHEONGDAM.md)|도산 KREAM 팝업 방문권|브랜드 콘셉트 탐방 · IP 팝업 방문|패션·향·팬덤 IP · 플래그십 공간 연출|
|7|[삼성·코엑스](../seoul-area-tagging/areas/SEOUL_SAMSUNG_COEX.md)|스타필드 코엑스몰 행사장|세계관 체험 · 테마형 팝업 탐방|영화 체험존 · 디저트 참여 행사|
|8|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|소공 롯데타운 팝업 방문권|브랜드 미션 체험 · 패션 팝업 탐방|브랜드 결합 방탈출 · K패션 테마|
|9|[이태원·한남](../seoul-area-tagging/areas/SEOUL_ITAEWON_HANNAM.md)|무신사 스페이스 한남|패션 콘셉트 팝업 탐방|데님·스포츠웨어 · 브랜드별 공간 재해석|
|10|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|영등포 타임스퀘어 팝업권|디저트·패션 팝업 탐방|식음 전문 팝업 공간 · 시즌별 상품 교체|
|11|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|북촌 한옥 팝업 방문권|한옥 브랜드 체험|한옥 전체 공간 연출 · 여행 준비 동선|
|12|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|신세계 강남점 팝업권|캐릭터 IP·식음 팝업 탐방|피규어·캐릭터 상품 · 식음 협업|
|13|[신촌·이대](../seoul-area-tagging/areas/SEOUL_SINCHON_EWHA.md)|현대백화점 신촌 유플렉스|캐릭터·뷰티 팝업 방문|뷰티 제품 체험 · 캐릭터 굿즈|
|14|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|필운 원모어백|작가·브랜드 기획 팝업 탐방|문구·도자기 · 독립 잡화점 기획|
|15|[강동](../seoul-area-tagging/areas/SEOUL_GANGDONG.md)|현대백화점 천호점|식품·캐릭터 상품 팝업 방문|간식 팝업 · 유아동 캐릭터 협업|
|16|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|마곡 원그로브 교보문고|문구·브랜드 부스 방문|신학기 문구 · 서점 연계 가이드북 부스|


## 전시 (EXHIBITION)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|소격 국립현대미술관 서울|현대미술 관람 · 기획전 선택|동시대 작가·미디어 전시 · 서울관 소장품|
|1|[이태원·한남](../seoul-area-tagging/areas/SEOUL_ITAEWON_HANNAM.md)|용산동6가 국립중앙박물관|역사·유물 관람 · 주제별 전시 탐방|상설전시관 · 주제별 전시해설|
|3|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|한강로 아모레퍼시픽미술관|현대미술 기획전 관람|예약형 기획전 · 작가·작품 해설|
|4|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|방이 소마미술관|미술·조각 관람|관내 전시·조각공원 연계|
|5|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|예술의전당 운영 전시장권|미술·디자인·서예 전시 관람|7전시실·디자인미술관·서예박물관 · 본관 공사 제외|
|6|[압구정·청담](../seoul-area-tagging/areas/SEOUL_APGUJEONG_CHEONGDAM.md)|청담 송은|동시대 미술 기획전 관람|젊은 작가 지원 · 매체별 기획|
|7|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|마곡 스페이스K 서울|현대미술 작가전 관람|작가 중심 기획 · 관람 해설|
|8|[구로·금천](../seoul-area-tagging/areas/SEOUL_GURO_GEUMCHEON.md)|독산 서서울미술관|뉴미디어 미술 관람|뉴미디어 특화 · 통유리 전시공간|
|9|[서울역·남대문](../seoul-area-tagging/areas/SEOUL_SEOUL_STATION_NAMDAEMUN.md)|봉래 문화역서울284|문화예술 기획전 관람|옛 역사의 전시 활용 · 행사 교체형|
|10|[북한산·강북](../seoul-area-tagging/areas/SEOUL_BUKHANSAN_GANGBUK.md)|부암 석파정 서울미술관|현대미술 기획전 관람|미술관·석파정 결합|
|11|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|을지로7가 DDP 전시권|디자인 전시 관람|디자인둘레길 · 공간별 특별전|
|12|[대학로·동대문](../seoul-area-tagging/areas/SEOUL_DAEHAKRO_DONGDAEMUN.md)|동숭 아르코미술관|시각예술 기획전 관람|소장품 기획 · 연구·창작 전시|
|13|[왕십리·건대](../seoul-area-tagging/areas/SEOUL_WANGSIMNI_KONKUK.md)|광장 빛의 시어터|몰입형 미디어아트 관람|빛·음악·공간 연출|
|14|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|옥인 박노수미술관|작가 작품·생활공간 관람|한국화 · 화가의 가옥 · 컬렉션|
|15|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|남현 남서울미술관|미술 기획전 관람|고전 건축·현대미술 결합|
|16|[은평·서대문](../seoul-area-tagging/areas/SEOUL_EUNPYEONG_YEONHUI.md)|연희 서대문자연사박물관|자연사 전시 관람|자연사 주제 · 교육형 관람|
|17|[신촌·이대](../seoul-area-tagging/areas/SEOUL_SINCHON_EWHA.md)|대현 이화여자대학교박물관|유물·공예 관람|도자·복식·서화 · 대학박물관|
|18|[마포](../seoul-area-tagging/areas/SEOUL_MAPO_GONGDEOK.md)|상암 한국영화박물관|영화 역사·기획전 관람|연대기 상설전시 · 영화 매체 체험|
|19|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|망원 서울함공원|함정 내부 관람|퇴역 함정 · 선박 내부 전시|
|20|[청량리](../seoul-area-tagging/areas/SEOUL_CHEONGNYANGNI_JUNGNANG.md)|제기 서울약령시한의약박물관|한의약 전시 관람|전통의학 주제 · 약령시 맥락|
|21|[여의도](../seoul-area-tagging/areas/SEOUL_YEOUIDO.md)|더현대 서울 ALT.1|유료 기획전 관람|몰 내 독립 전시공간|
|22|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|성수 수도박물관 본관|수도 역사·유물 관람|옛 송수실 · 산업유산 전시|
|23|[도봉·노원](../seoul-area-tagging/areas/SEOUL_DOBONG_NOWON.md)|중계 노원아트뮤지엄|미술 기획전 관람|시립미술관 협력전 · 문화회관 전시공간|
|24|[홍대·연남](../seoul-area-tagging/areas/SEOUL_HONGDAE_YEONNAM.md)|서교 상상마당 갤러리|문화공간 기획전 관람|홍대 복합문화공간 · 갤러리|
|25|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|문래 술술센터|실험예술 기획전 관람|예술·기술 융복합 · 술술갤러리|
|26|[강남·역삼](../seoul-area-tagging/areas/SEOUL_GANGNAM_YEOKSAM.md)|역삼 일상비일상의틈|참여형 아트 관람|기술·신진작가 · 참여형 전시|
|27|[강동](../seoul-area-tagging/areas/SEOUL_GANGDONG.md)|강동아트센터 아트랑|청년 작가 전시 관람|공연장 내 전시공간|
|28|[목동·양천](../seoul-area-tagging/areas/SEOUL_MOKDONG_YANGCHEON.md)|양천문화회관 전시관|기획·협력전 관람|주제별 일정형 전시 · 지역 문화회관|


## 산책 (WALK)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|통인·누하·옥인 골목–수성동계곡 연결|문화 골목 산책 · 계곡 연결 걷기|한옥·예술가 흔적 · 골목·자연 연결|
|2|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|북촌 공개 한옥 골목의 주간 탐방권|한옥 골목 산책 · 역사문화 탐방|미로형 골목 · 한옥 지붕 · 주간 공개 구간|
|3|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|석촌호수 둘레 산책권|호수 둘레 산책|순환 경로 · 수변·도심 풍경|
|4|[대학로·동대문](../seoul-area-tagging/areas/SEOUL_DAEHAKRO_DONGDAEMUN.md)|이화동–낙산 성곽 산책권|성곽·골목 산책|벽화 골목 · 성벽을 따라 걷기|
|5|[도봉·노원](../seoul-area-tagging/areas/SEOUL_DOBONG_NOWON.md)|공릉 경춘선숲길 구간|철길 산책 · 동네 탐방|보존 철길 · 선형 녹지|
|6|[홍대·연남](../seoul-area-tagging/areas/SEOUL_HONGDAE_YEONNAM.md)|경의선숲길 연남 구간·인접 골목|숲길·골목 산책|옛 철길 재생 · 연남 골목 연결|
|7|[여의도](../seoul-area-tagging/areas/SEOUL_YEOUIDO.md)|여의도한강공원–물빛광장 수변|강변 산책|광장·한강 수변 연결|
|8|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|반포 수변–서래섬 연결권|강변·섬 산책|수변 경로 · 서래섬 연결|
|9|[왕십리·건대](../seoul-area-tagging/areas/SEOUL_WANGSIMNI_KONKUK.md)|자양 뚝섬한강공원 수변|강변 산책|자양 한강 보행권|
|10|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|선유도–선유교–양화한강공원 연결|재생 공원·수변 보행|정수장 재생 · 보행교 연결|
|11|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|서울숲의 안내 산책권|도시숲 산책|생태 공간 · 공원 내 경로 선택|
|12|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|서울식물원 호수원 수변가로·관찰데크|호수·생태 산책|호수 횡단 보행교 · 수변 관찰|
|13|[구로·금천](../seoul-area-tagging/areas/SEOUL_GURO_GEUMCHEON.md)|항동 푸른수목원·저수지 산책권|주제정원·저수지 산책|수목원 정원 · 수변 경로|
|14|[강동](../seoul-area-tagging/areas/SEOUL_GANGDONG.md)|암사 생태탐방로–강변전망데크 구간|생태 수변 산책|갈대·물웅덩이 · 강변 탐방 경로|
|15|[은평·서대문](../seoul-area-tagging/areas/SEOUL_EUNPYEONG_YEONHUI.md)|연희숲속쉼터·인접 홍제천|숲·하천 산책|녹지·수변 연결|
|16|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|망원 골목–망원정·강변 연결|동네·강변 도보 탐방|생활 골목 · 정자·수변 연결|
|17|[서울역·남대문](../seoul-area-tagging/areas/SEOUL_SEOUL_STATION_NAMDAEMUN.md)|서울로7017–만리재 연결|도심 보행 탐방|고가 보행길 · 주변 동네 연결|
|18|[마포](../seoul-area-tagging/areas/SEOUL_MAPO_GONGDEOK.md)|대흥·염리 경의선숲길 구간|도심 숲길 산책|옛 철길 녹지 · 생활권 연결|
|19|[북한산·강북](../seoul-area-tagging/areas/SEOUL_BUKHANSAN_GANGBUK.md)|성북동 문화유산 도보권|문화유산 골목 탐방|옛집·역사문화 지점 · 성북동 골목|
|20|[청량리](../seoul-area-tagging/areas/SEOUL_CHEONGNYANGNI_JUNGNANG.md)|묵동 중랑장미공원·인접 중랑천|정원·하천 산책|장미터널 구조 · 수변 보행|
|21|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|신대방 보라매공원 산책권|평지 공원 산책|정원·중앙트랙 · 공원 내 걷기|
|22|[목동·양천](../seoul-area-tagging/areas/SEOUL_MOKDONG_YANGCHEON.md)|신월 서서울호수공원 호숫가|호수·정원 산책|정수장 재생 · 호숫가 경로|
|23|[삼성·코엑스](../seoul-area-tagging/areas/SEOUL_SAMSUNG_COEX.md)|선정릉 공개 관람 숲길|역사 숲길 산책|왕릉·숲 · 유산 관람 경로|
|24|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|노들섬 개방 수변 구간|섬 수변 산책|한강 조망 · 재개장 공개 구간|
|25|[이태원·한남](../seoul-area-tagging/areas/SEOUL_ITAEWON_HANNAM.md)|서빙고 용산가족공원|연못·공원 산책|박물관 공원권 · 연못 경로|
|26|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|명동 보행 상권–남산예장자락 연결|상업거리·공원 산책|도심 골목 · 녹지 연결|
|27|[압구정·청담](../seoul-area-tagging/areas/SEOUL_APGUJEONG_CHEONGDAM.md)|신사 도산공원 산책권|역사공원 산책|도산공원 내부 경로|
|28|[강남·역삼](../seoul-area-tagging/areas/SEOUL_GANGNAM_YEOKSAM.md)|율현공원 정원·수변 경로|정원·수변 산책|잔디광장 · 장미원 경로|
|29|[신촌·이대](../seoul-area-tagging/areas/SEOUL_SINCHON_EWHA.md)|연세로·신촌기차역·이화여대길 골목권|대학가 거리 구경|역사 거리 지점 · 대학가 골목|


## 공원 (PARK)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|서울숲 가족마당·연못·공개 도시숲|도시숲 휴식 · 가족 나들이 · 자연 관찰|가족마당 · 연못과 테마숲|
|2|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|올림픽공원 피크닉장·몽촌해자 공개 구간|피크닉 · 잔디 나들이 · 수변 휴식|몽촌해자 · 토성 주변 공원|
|3|[마포](../seoul-area-tagging/areas/SEOUL_MAPO_GONGDEOK.md)|상암 평화의공원 난지연못·평화의정원|연못 휴식 · 정원 나들이 · 피크닉|난지연못 · 환경재생 공원|
|4|[여의도](../seoul-area-tagging/areas/SEOUL_YEOUIDO.md)|여의도한강공원 여의나루·물빛광장 주변|강변 휴식 · 수변 나들이|도심 한강 체류 · 물빛광장 주변|
|5|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|마곡 서울식물원 열린숲·호수원 공개 구간|정원·호수 휴식 · 식물 관찰|무료 공개 공원 · 호수원 관찰데크|
|6|[북한산·강북](../seoul-area-tagging/areas/SEOUL_BUKHANSAN_GANGBUK.md)|번동 북서울꿈의숲 월영지·잔디광장|연못 휴식 · 숲그늘 · 잔디 나들이|월영지 · 숲으로 둘러싸인 공원|
|7|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|신대방 보라매공원 옥만호·정원|연못·정원 휴식 · 가족 나들이|정원박람회 조성 정원 · 에어파크 주변|
|8|[왕십리·건대](../seoul-area-tagging/areas/SEOUL_WANGSIMNI_KONKUK.md)|능동 서울어린이대공원 공개 녹지·정원|가족 소풍 · 녹지 휴식 · 자연 나들이|공원 무료 입장 · 가족 중심 공원|
|9|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|반포동 반포한강공원 세빛섬 인접 수변|강변 휴식 · 수변 나들이|섬 인접 수변 · 반포 공원 체류|
|10|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|양화동 선유도공원 공개 정원·선유마당|정원 휴식 · 수변 공원 나들이|정수장 구조 재생 · 수생식물 정원|
|11|[이태원·한남](../seoul-area-tagging/areas/SEOUL_ITAEWON_HANNAM.md)|용산동6가 용산가족공원 연못·잔디광장|잔디 휴식 · 연못 나들이 · 가족 소풍|잔디·숲·연못 · 가족공원 체류|
|12|[구로·금천](../seoul-area-tagging/areas/SEOUL_GURO_GEUMCHEON.md)|항동 푸른수목원 정원·항동저수지|정원 휴식 · 식물 관찰 · 저수지 나들이|주제정원 · 항동저수지|
|13|[목동·양천](../seoul-area-tagging/areas/SEOUL_MOKDONG_YANGCHEON.md)|신월 서서울호수공원 중앙호수·몬드리안정원|호수 휴식 · 정원 나들이|정수장 재생 · 몬드리안정원|
|14|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|망원동 망원한강공원 공개 수변·휴식 구간|강변 휴식 · 나들이|생활권 한강 공원 · 지정 그늘막 구역|
|15|[강동](../seoul-area-tagging/areas/SEOUL_GANGDONG.md)|암사동 광나루한강공원 공개 휴식 구간|자연형 수변 휴식 · 가족 나들이|갈대·철새 서식 환경 · 강동 수변 공원|
|16|[강남·역삼](../seoul-area-tagging/areas/SEOUL_GANGNAM_YEOKSAM.md)|율현동 율현공원 잔디광장·공개 정원|잔디 휴식 · 정원 나들이 · 책쉼터|주거지 공원 체류 · 저류지·장미원|
|17|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|이촌동 이촌한강공원 공개 강변 구간|강변 휴식 · 자연 나들이|이촌 수변 체류 · 강변 녹지|
|18|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|삼청동 삼청근린공원 공개 숲·쉼터|숲그늘 휴식 · 가벼운 공원 나들이|도심 계곡형 숲 · 기존 수목과 쉼터|
|19|[도봉·노원](../seoul-area-tagging/areas/SEOUL_DOBONG_NOWON.md)|공릉동 화랑대철도공원 공개 철길정원|정원 휴식 · 기차 구경 나들이|폐역·철도차량 · 철길 공원 재생|
|20|[은평·서대문](../seoul-area-tagging/areas/SEOUL_EUNPYEONG_YEONHUI.md)|연희동 연희숲속쉼터 공개 정원·쉼터|숲속 쉼터 · 정원 나들이|허브원 · 안산 자락 녹지|
|21|[청량리](../seoul-area-tagging/areas/SEOUL_CHEONGNYANGNI_JUNGNANG.md)|묵동 중랑장미공원 공개 정원·휴식 지점|정원 휴식 · 하천 나들이|장미터널 구조 · 묵동 수변 정원|
|22|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|청운동 청운공원 공개 휴게공간|언덕 공원 휴식 · 녹지 나들이|윤동주 시인의 언덕 · 문학 연계 휴게공간|
|23|[대학로·동대문](../seoul-area-tagging/areas/SEOUL_DAEHAKRO_DONGDAEMUN.md)|동숭동 낙산공원 중앙광장 주변|언덕 공원 휴식 · 도심 나들이|성곽 인접 휴식 · 고지대 공원|
|24|[압구정·청담](../seoul-area-tagging/areas/SEOUL_APGUJEONG_CHEONGDAM.md)|신사동 도산공원 내부 휴식 구간|도심 녹지 휴식 · 짧은 공원 나들이|도산 안창호 기념공원 · 상업지 인접 쉼터|
|25|[서울역·남대문](../seoul-area-tagging/areas/SEOUL_SEOUL_STATION_NAMDAEMUN.md)|현저동 서대문독립공원 공개 휴식 구간|역사공원 휴식 · 도심 나들이|독립문 주변 녹지 · 독립기념 공간|
|26|[홍대·연남](../seoul-area-tagging/areas/SEOUL_HONGDAE_YEONNAM.md)|연남동 경의선숲길 공개 녹지·쉼터|생활 녹지 휴식 · 짧은 나들이|옛 철길 선형 공원 · 골목 인접 쉼터|
|27|[삼성·코엑스](../seoul-area-tagging/areas/SEOUL_SAMSUNG_COEX.md)|삼성동 삼성해맞이공원 잔디·파고라|잔디·쉼터 휴식 · 짧은 공원 나들이|배수지 상부 공원 · 전망데크 인접 쉼터|
|28|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|예장동 남산예장공원 샛자락 쉼터|도심 녹지 휴식 · 잠깐 쉬기|예장자락 재생 · 생태교량 인접 쉼터|


## 공연 (PERFORMANCE)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[대학로·동대문](../seoul-area-tagging/areas/SEOUL_DAEHAKRO_DONGDAEMUN.md)|동숭·명륜 대학로 아르코·대학로예술극장 인접 극장권|연극 관람 · 무용 관람 · 극장 탐방|인접 복수 극장 · 창작 공연 선택|
|2|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|서초동 예술의전당 오페라하우스·음악당|연극·무용 관람 · 음악 공연 관람|여러 전문 공연장 · 장르별 반복 편성|
|3|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|세종로 세종문화회관 대극장·M·S·체임버홀|다장르 공연 관람|연극·오페라·발레 · 시즌 기획|
|4|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|마곡동 LG아트센터 서울|연극·음악 관람 · 뮤지컬 관람|두 공연공간 · 다장르 기획|
|5|[홍대·연남](../seoul-area-tagging/areas/SEOUL_HONGDAE_YEONNAM.md)|서교동 상상마당 라이브홀 중심 홍대 인접 공연권|라이브 음악 관람 · 공연 탐방|인디·밴드 문화 · 라이브홀 편성|
|6|[이태원·한남](../seoul-area-tagging/areas/SEOUL_ITAEWON_HANNAM.md)|한남동 블루스퀘어 공연장|뮤지컬 관람 · 공연 방문|장기 뮤지컬 편성 · 한남 전문 공연장|
|7|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|신천동 롯데콘서트홀|음악 공연 관람 · 마티네|콘서트홀 · 음악 기획 시리즈|
|8|[강남·역삼](../seoul-area-tagging/areas/SEOUL_GANGNAM_YEOKSAM.md)|역삼동 GS아트센터|뮤지컬 관람 · 라이브 콘서트|장기 작품·후속 편성 · 역삼 전문 공연장|
|9|[압구정·청담](../seoul-area-tagging/areas/SEOUL_APGUJEONG_CHEONGDAM.md)|신사동 광림아트센터 BBCH홀|뮤지컬 관람|뮤지컬 전문 방문 · 작품별 예매|
|10|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|와룡동 서울돈화문국악당|국악 관람 · 전통음악 감상|가야금·판소리·정가 · 국악 전용 공연장|
|11|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|명동1가 명동예술극장|연극 관람|국립극단 제작 · 연속 작품 편성|
|12|[왕십리·건대](../seoul-area-tagging/areas/SEOUL_WANGSIMNI_KONKUK.md)|광장동 YES24 LIVE HALL|아티스트 콘서트 · 팬콘서트|라이브 콘서트홀 · 아티스트별 회차|
|13|[삼성·코엑스](../seoul-area-tagging/areas/SEOUL_SAMSUNG_COEX.md)|삼성동 코엑스아티움 우리은행홀|공연·콘서트 관람|아티움 공연장 · 아티스트별 편성|
|14|[마포](../seoul-area-tagging/areas/SEOUL_MAPO_GONGDEOK.md)|대흥동 마포아트센터 아트홀맥·플레이맥|클래식 관람 · 기획 공연|M 클래식 시리즈 · 복수 공연공간|
|15|[강동](../seoul-area-tagging/areas/SEOUL_GANGDONG.md)|상일동 강동아트센터|발레·클래식 관람 · 가족 공연|다장르 연간 기획 · 전문 공연장|
|16|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|상수동 제비다방 공연공간|가까이 듣는 라이브 · 인디 음악|다방 결합 공연 · 자율 기부 운영|
|17|[도봉·노원](../seoul-area-tagging/areas/SEOUL_DOBONG_NOWON.md)|중계동 노원문화예술회관|기획·지역 공연 관람|문화예술회관 · 음악·낭독 편성|
|17|[목동·양천](../seoul-area-tagging/areas/SEOUL_MOKDONG_YANGCHEON.md)|신정동 양천문화회관 대극장|관현악·무용 관람 · 콘서트|회관 다장르 편성 · 기획·대관 구분|
|17|[은평·서대문](../seoul-area-tagging/areas/SEOUL_EUNPYEONG_YEONHUI.md)|녹번동 은평문화예술회관 공연장|클래식·민요 관람 · 무용 공연|지역 예술단·기획 · 회관별 예약|
|20|[서울역·남대문](../seoul-area-tagging/areas/SEOUL_SEOUL_STATION_NAMDAEMUN.md)|정동 국립정동극장 세실|전통 공연 관람 · 창작 무대|정동 공연공간 · 작품별 편성|
|21|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|노들섬 라이브하우스·해당 회차 공개 공연공간|라이브 관람 · 야외 공연|섬 공연공간 · 기획·대관 편성|
|22|[청량리](../seoul-area-tagging/areas/SEOUL_CHEONGNYANGNI_JUNGNANG.md)|회기동 경희대학교 평화의전당|콘서트 관람|대학 대형 공연장 · 아티스트별 예매|
|23|[북한산·강북](../seoul-area-tagging/areas/SEOUL_BUKHANSAN_GANGBUK.md)|번동 꿈의숲아트센터 공연장|가족 뮤지컬 · 마티네 음악|퍼포먼스홀·콘서트홀 · 연령별 편성|
|24|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|문래동 문래예술공장 박스씨어터|창작 연극 관람|예술공장 공연공간 · 실험 무대|
|25|[구로·금천](../seoul-area-tagging/areas/SEOUL_GURO_GEUMCHEON.md)|독산동 금천뮤지컬센터 금천예술극장|연극 관람 · 창작 공연|뮤지컬센터 공연공간 · 작품별 예약|
|26|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|성수동 공간 철(CHUL)|뮤지컬 관람|예약형 공연공간 · 성수 창작 무대|
|27|[신촌·이대](../seoul-area-tagging/areas/SEOUL_SINCHON_EWHA.md)|창천동 유플렉스 앞 스타광장|K팝 거리무대 · 신인 공연|대학가 광장 공연 · 정례화 발표|
|28|[여의도](../seoul-area-tagging/areas/SEOUL_YEOUIDO.md)|여의도동 KBS 신관 공개홀|음악 공연 방청|방송 녹화 공연 · 추첨 방청|
|29|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|신림동 관악아트홀 전시실 리딩공연|리딩공연 관람|장소 변경 공연 · 소규모 예약|


## 서점 (BOOKSTORE)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[은평·서대문](../seoul-area-tagging/areas/SEOUL_EUNPYEONG_YEONHUI.md)|연희동 유어마인드 오프라인 책방|독립출판물 발견 · 아트북 탐색|독립 창작자 출판물 · 책갈피·인쇄물|
|2|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|합정동 비플랫폼|아트북 탐색 · 소량 에디션 발견|직접 짓는 책 · 해외 서적·제본|
|3|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|종로1가 교보문고 광화문점|여러 분야 도서 탐색 · 책 구경·구매|도심 종합 판매 서점 · 가족 도서 탐색|
|4|[강남·역삼](../seoul-area-tagging/areas/SEOUL_GANGNAM_YEOKSAM.md)|역삼동 최인아책방 본점|선별 도서 탐색 · 작가 북토크|주제 도서 판매 · 작가 대화 프로그램|
|5|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|용산동2가 고요서사|문학 도서 발견 · 낭독·읽기|문학 전문 서가 · 읽기 중심 큐레이션|
|6|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|통의동 책방오늘|문학 취향 서가 탐색|작가 취향 큐레이션 · 서촌 독립서점|
|7|[왕십리·건대](../seoul-area-tagging/areas/SEOUL_WANGSIMNI_KONKUK.md)|자양동 인덱스숍 판매 서가|분류 서가 탐색 · 매거진 발견|알파벳 분류 · 서점·북카페 결합|
|8|[서울역·남대문](../seoul-area-tagging/areas/SEOUL_SEOUL_STATION_NAMDAEMUN.md)|회현동 스틸북스 회현|취향 도서 발견 · 선별 서가 탐방|재생 건물 서점 · 큐레이션 도서 판매|
|9|[대학로·동대문](../seoul-area-tagging/areas/SEOUL_DAEHAKRO_DONGDAEMUN.md)|혜화동 동양서림|오래된 서점 탐방 · 도서 선택|미래유산 서점 · 같은 자리의 서점 문화|
|10|[신촌·이대](../seoul-area-tagging/areas/SEOUL_SINCHON_EWHA.md)|창천동 홍익문고|지역 서점 탐방 · 추천 도서 탐색|직원 큐레이션 · 신촌 서점 문화|
|11|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|성수동1가 서울숲길 낫저스트북스|독립서점 탐방 · 책 구매·읽기|서울숲길 서가 · 도서·필사 연계|
|12|[마포](../seoul-area-tagging/areas/SEOUL_MAPO_GONGDEOK.md)|성산동 책방밀물|동네 서가 탐방 · 읽기·쓰기 모임|동네 책방 · 예약형 독서 활동|
|13|[압구정·청담](../seoul-area-tagging/areas/SEOUL_APGUJEONG_CHEONGDAM.md)|청담동 10 꼬르소 꼬모 책 판매 구간|책·라이프스타일 탐색|편집 매장 결합 · 문화 상품 속 책|
|14|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|서초동 교보문고 강남점|도서 탐색·구매|교보타워 지하 판매 서점 · 매장 재고 검색|
|14|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|신천동 교보문고 잠실점|도서 탐색·구매|잠실 판매 서점 · 매장 재고 검색|
|14|[목동·양천](../seoul-area-tagging/areas/SEOUL_MOKDONG_YANGCHEON.md)|목동 CBS 지하 교보문고 목동점|도서 탐색·구매|CBS 지하 판매 서점 · 매장 재고 검색|
|14|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|마곡동 원그로브 교보문고|도서 탐색·구매|원그로브 판매 서점 · 매장 재고 검색|
|14|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|영등포동4가 타임스퀘어 교보문고|도서 탐색·구매|몰 내부 판매 서점 · 매장 재고 검색|
|14|[삼성·코엑스](../seoul-area-tagging/areas/SEOUL_SAMSUNG_COEX.md)|삼성동 코엑스몰 영풍문고|도서 탐색·구매 · 가족 책 구경|판매 서점 · 어린이 책 공간|
|14|[여의도](../seoul-area-tagging/areas/SEOUL_YEOUIDO.md)|여의도동 IFC몰 영풍문고|도서 탐색·구매|몰 내부 판매 서점 · 추천 도서 안내|
|14|[강동](../seoul-area-tagging/areas/SEOUL_GANGDONG.md)|천호동 교보문고 천호점|도서 탐색·구매|분야별 서가 · 매장 재고 검색|
|14|[구로·금천](../seoul-area-tagging/areas/SEOUL_GURO_GEUMCHEON.md)|가산동 마리오아울렛3 영풍문고|도서 탐색·구매|아울렛 내부 판매 서점 · 현행 도서 행사|
|14|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|신림동 타임스트림 영풍문고|도서 탐색·구매|복합상가 판매 서점 · 현행 도서 행사|


## 공방 (WORKSHOP)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|가회동 전통공예체험관|전통 소품 만들기 · 공예 체험 선택|한지·닥종이 · 북촌 체험공간|
|2|[왕십리·건대](../seoul-area-tagging/areas/SEOUL_WANGSIMNI_KONKUK.md)|용답동 서울새활용플라자 체험교육|새활용 제작 · 소재 탐구|봉제·수선 · 장난감·종이 재사용|
|3|[강동](../seoul-area-tagging/areas/SEOUL_GANGDONG.md)|성내동 성안로 엔젤공방거리|공방 체험 선택 · 소품 제작|청년 공방거리 · 밀랍초·향수·양말목|
|4|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|합정동 비플랫폼 책 짓기 공간|제본 체험 · 책 제작|독립출판 · 북디자인·더미북|
|5|[홍대·연남](../seoul-area-tagging/areas/SEOUL_HONGDAE_YEONNAM.md)|연남동 연플레이스 베이킹 스튜디오|베이킹 체험 · 완성 디저트 포장|반죽·굽기·꾸미기 · 초보자 안내|
|5|[신촌·이대](../seoul-area-tagging/areas/SEOUL_SINCHON_EWHA.md)|창천동 연플레이스 신촌점|베이킹 체험 · 친구 제작 활동|완성 디저트 포장 · 모임형 체험|
|7|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|성수동1가 형공방|가죽 카드지갑 제작|새들스티치 · 당일 실용 소품|
|8|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|송리단길 노트수 스튜디오|매듭팔찌 만들기|색·길이 선택 · 착용 소품 당일 완성|
|9|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|문래동2가 댕댕라탄|라탄 소품 제작|엮기·꾸미기 · 도어벨 결과물|
|10|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|방배동 키논 목공방 나무반지 수업|나무반지 제작|대패 가공 · 목재 결·연마|
|11|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|후암동 비라이트 캔들공방|캔들 만들기|향·색 선택 · 몰드·장식|
|12|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|누하동 상촌재 체험 프로그램|전통 소품 제작|향낭·댕기 · 한옥 문화공간|
|13|[도봉·노원](../seoul-area-tagging/areas/SEOUL_DOBONG_NOWON.md)|월계동 초안산도자기체험장|도자기 만들기 · 초벌접시 페인팅|일일 도예 · 가마 후 별도 수령|
|14|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|신림동 작업실 WOOM 우드카빙|캐릭터 목조각|조각·채색 · 개인 디자인|


## 힙한 (TRENDY)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|성수 연무장길·대림창고 인접 상업 골목|공장 재생 카페 · 브랜드 공간 탐색 · 편집숍 구경|산업 흔적과 새 브랜드의 공존 · 공간 자체가 방문 콘텐츠|
|2|[이태원·한남](../seoul-area-tagging/areas/SEOUL_ITAEWON_HANNAM.md)|한남동 W한남 인접 브랜드 상업권|취향형 패션·라이프스타일 · 큐레이션 공간 · 브랜드 체험|색·취향 중심 편집 · 쇼룸형 공간 경험|
|3|[홍대·연남](../seoul-area-tagging/areas/SEOUL_HONGDAE_YEONNAM.md)|서교동 홍대 R3 인접 패션 골목|새 패션 취향 탐색 · 코스메틱 · 젊은 거리문화|Y2K·발레코어 스타일 · 패션 중심 상업 골목|
|4|[압구정·청담](../seoul-area-tagging/areas/SEOUL_APGUJEONG_CHEONGDAM.md)|도산공원 인접 브랜드 공간권|디자인 플래그십 · 브랜드 세계관 · 공간 감상|하우스도산 · 아웃도어·패션의 공간 실험|
|5|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|북촌·삼청 한옥 브랜드 공간권|한옥 브랜드 공간 · 향·공예 취향 · 전통 공간의 재해석|한옥과 현대 브랜드 · 건축·상품 경험 결합|
|6|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|신당동 퇴계로81·83길 인접 카페 골목|신규 디저트 공간 · 테이블웨어 감상 · 골목 카페 탐색|한국 재료·도자기 · 작은 브랜드의 감각|
|7|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|문래동2·3가 예술촌 상업 골목|재생 공간 카페 · 예술 골목 구경 · 레트로 공간|철공소와 창작의 공존 · 공장 재료의 질감|
|8|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|신용산·삼각지 용리단길 상업 골목|감각적 카페 · 디자인 상점 · 골목 미식|옛 골목의 상업 재해석 · 작은 취향 공간|
|9|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|망원동 포은로 망리단길 인접 골목|소형 편집숍 · 개성 있는 카페 · 생활 골목 구경|독립 소품 취향 · 주거지와 작은 상점|
|10|[여의도](../seoul-area-tagging/areas/SEOUL_YEOUIDO.md)|더현대 서울 내부 브랜드·팝업 공간|새 브랜드 체험 · 실내 팝업 · 디자인 매장|한 건물의 교체형 콘텐츠 · 날씨와 무관한 취향 탐색|
|11|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|봉천동 샤로수길 상업 골목|개성 있는 음식점 · 골목 카페 · 청년 취향 탐색|대학가 로컬 상권 · 주택 골목의 작은 상점|
|12|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|마곡·발산 테라스 상업거리의 인접 구간|테라스 카페 · 에스프레소 바 · 저녁 미식|계획도시 보행 상권 · 노천 좌석 중심 체류|


## 활기찬 (LIVELY)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[홍대·연남](../seoul-area-tagging/areas/SEOUL_HONGDAE_YEONNAM.md)|서교동 홍대 걷고싶은거리·인접 공연 상권|거리 공연 · 사람 구경 · 쇼핑과 밤거리|참여형 거리문화 · 낮·저녁의 다른 활기|
|2|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|명동 중심 보행·쇼핑 상권의 주말·저녁|관광객 거리 · 상점 탐방 · 먹거리 구경|다국적 보행 상권 · 밝은 저녁 거리|
|3|[성수](../seoul-area-tagging/areas/SEOUL_SEONGSU.md)|성수 연무장길 중심 상업 골목의 매장 운영 시간|브랜드 공간 탐방 · 팝업 체험 · 카페 사이 보행|교체형 콘텐츠 유입 · 방문객의 골목 탐색|
|4|[청량리](../seoul-area-tagging/areas/SEOUL_CHEONGNYANGNI_JUNGNANG.md)|경동시장 본관·인접 청량리 시장 통로의 영업 시간|시장 거래 구경 · 먹거리 · 재생 극장 카페|상인과 방문객의 교차 · 생활시장과 새 방문층|
|5|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|노량진수산시장 소매·식사 방문권의 점심·저녁|수산물 고르기 · 시장 대화 · 현장 식사|직접 고르는 거래 체험 · 수산시장 실내 활기|
|6|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|석촌호수 동호 인접 방이맛골의 저녁 상권|야장 식사 · 저녁 모임 · 밝은 먹자골목|호수 산책 뒤 상권 이동 · 밤거리 체류|
|7|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|신용산·삼각지 용리단길 상업 골목의 주말·매장 운영 시간|골목 미식 · 카페 보행 · 방문객 구경|오래된 골목의 새 상점 · 소규모 그룹 체류|


## 조용한 (QUIET)

29개 Area 모두 근거 부족 판정이다. 조용한 장소가 없다는 뜻은 아니며 키워드나 하위 순위를 만들어 채우지 않았다.

## 경관 (SCENIC_VIEW)

|순위|지역|방문권|방문 매력|차별성·특화 요소|
|---:|---|---|---|---|
|1|[잠실·송파](../seoul-area-tagging/areas/SEOUL_JAMSIL_SONGPA.md)|잠실 롯데월드타워 서울스카이 전망대|도시 파노라마 · 한강 조망 · 일몰·야경|고층에서 내려다보는 서울 · 실내 전망과 유리 바닥|
|2|[대학로·동대문](../seoul-area-tagging/areas/SEOUL_DAEHAKRO_DONGDAEMUN.md)|종로구 낙산공원·인접 한양도성 조망 지점|성벽과 도시 · 노을 · 도심 야경|역사적 전경과 도시 불빛 · 야외 언덕 조망|
|3|[왕십리·건대](../seoul-area-tagging/areas/SEOUL_WANGSIMNI_KONKUK.md)|응봉산 팔각정 조망 지점|한강·교량 · 도시 야경 · 강변 철도 풍경|낮은 산의 높이차 · 강·철길·도시의 겹침|
|4|[관악·사당](../seoul-area-tagging/areas/SEOUL_GWANAK_SADANG.md)|본동 용양봉저정공원 전망대|한강대교·노들섬 · 도시 야경 · 해질녘 조망|남쪽 언덕의 강·섬 구도 · 공개 전망 데크|
|5|[여의도](../seoul-area-tagging/areas/SEOUL_YEOUIDO.md)|여의도공원 서울달 탑승 조망|한강과 빌딩숲 · 상공 경관 · 주야경|계류식 기구 수직 상승 · 고정 전망대와 다른 경험|
|6|[북한산·강북](../seoul-area-tagging/areas/SEOUL_BUKHANSAN_GANGBUK.md)|북서울꿈의숲 전망대|산 능선 · 공원과 도시 · 전망대 휴식|북한산·도봉산·수락산의 배경 · 북동부 조망|
|7|[마포](../seoul-area-tagging/areas/SEOUL_MAPO_GONGDEOK.md)|상암 하늘공원 정상부 전망 공간|한강 스카이라인 · 억새 전경 · 해질녘 하늘|높은 초원과 강의 대비 · 매립지 재생 경관|
|8|[서초·반포](../seoul-area-tagging/areas/SEOUL_SEOCHO_BANPO.md)|반포한강공원 세빛섬 인접 공개 수변|교량·강물 · 빛나는 섬 · 수면 야경|세빛섬 반사 · 도시 수변의 조명 구도|
|9|[상수·합정·망원](../seoul-area-tagging/areas/SEOUL_SANGSU_HAPJEONG_MANGWON.md)|망원한강공원 공개 수변의 석양 조망 지점|강변 노을 · 넓은 하늘 · 물빛 휴식|낮은 수변 시선 · 해질녘 색 변화|
|10|[영등포·문래](../seoul-area-tagging/areas/SEOUL_YEONGDEUNGPO_MULLAE.md)|선유도공원 수변·양화대교 접근 조망 구간|강과 맞은편 공원 · 교량 풍경 · 재생 정원|섬의 수변 시선 · 정수장 구조와 식생|
|11|[용산](../seoul-area-tagging/areas/SEOUL_YONGSAN.md)|노들섬 재개장 수변문화공간의 공개 구역|한강 물빛 · 교량 옆 경관 · 수변 체류|강 가운데 섬의 시선 · 재정비 중 부분 개방|
|12|[삼성·코엑스](../seoul-area-tagging/areas/SEOUL_SAMSUNG_COEX.md)|삼성해맞이공원 전망 지점|한강·청담대교 · 잠실 스카이라인 · 언덕 전망|동남권 도시 구도 · 한강 남안의 시선|
|13|[북촌·인사동·종로](../seoul-area-tagging/areas/SEOUL_BUKCHON_INSADONG_IKSUN.md)|가회동 북촌8경의 공개 골목 조망 지점|한옥 지붕 · 골목의 깊이 · 전통 도시 경관|한옥 지붕의 연속 · 높낮이 있는 생활 골목|
|14|[광화문·서촌](../seoul-area-tagging/areas/SEOUL_GWANGHWAMUN_SEOCHON.md)|옥인동 수성동계곡 공개 관찰 지점|바위 계곡 · 인왕산 배경 · 기린교 풍경|복원된 진경산수 · 도심 속 근경|
|15|[은평·서대문](../seoul-area-tagging/areas/SEOUL_EUNPYEONG_YEONHUI.md)|홍제천 인공폭포 맞은편 공개 감상 지점|폭포 물줄기 · 수변 휴식 · 폭포 사진|절벽과 도심 수변 · 가까운 시선의 물 경관|
|16|[강서·김포](../seoul-area-tagging/areas/SEOUL_GANGSEO_MAGOK.md)|서울식물원 호수원 수변가로·호수횡단 보행교|호수 반영 · 온실·도시 배경 · 수변 정원|열린 수면과 현대 건축 · 보행교의 시점 변화|
|17|[목동·양천](../seoul-area-tagging/areas/SEOUL_MOKDONG_YANGCHEON.md)|서서울호수공원 중앙호수·낙우송 전망대|호수와 녹음 · 재생 정원 · 수목 프레임|정수장 흔적 · 호수를 내려다보는 정원 시점|
|18|[구로·금천](../seoul-area-tagging/areas/SEOUL_GURO_GEUMCHEON.md)|푸른수목원 항동저수지 수변|저수지 반영 · 버들·갈대 · 계절 수생식물|주제정원 옆 잔잔한 수면 · 저녁 역광|
|19|[강동](../seoul-area-tagging/areas/SEOUL_GANGDONG.md)|암사생태공원 한강전망데크|한강과 하중도 · 갈대·억새 · 자연형 둔치|낮은 생태 수변 · 물새 쉼터의 전경|
|20|[서울역·남대문](../seoul-area-tagging/areas/SEOUL_SEOUL_STATION_NAMDAEMUN.md)|서울로7017 공개 공중보행로의 도심 조망 구간|도시와 길 · 서울역 주변 · 고가 보행 풍경|차량 고가의 사람길 전환 · 낮은 공중 시선|
|21|[명동·을지로](../seoul-area-tagging/areas/SEOUL_MYEONGDONG_EULJIRO.md)|을지로7가 DDP 예약형 루프탑 투어|도시 지붕 조망 · 건축 곡면 · 동대문 전경|평소 접근하지 않는 지붕 · 안전장비 동반 투어|
|22|[도봉·노원](../seoul-area-tagging/areas/SEOUL_DOBONG_NOWON.md)|공릉동 화랑대철도공원 불빛정원 점등 구간|야간 조명정원 · 빛 터널 · 철도 배경 사진|옛 역·기차와 빛 · 근거리 야간 연출|

