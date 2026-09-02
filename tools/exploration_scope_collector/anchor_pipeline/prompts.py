from __future__ import annotations

import json
from typing import Any


def normalize_prompt(
    *,
    city: str,
    collection_region: dict[str, Any],
    source: dict[str, Any],
    source_text: str,
    existing_anchors: list[dict[str, Any]],
) -> str:
    return f"""
목표: 제공된 공식·공공 원문에서 나들이 코스의 기준점이 될 AREA/HUB 후보만 추출한다.

큰 지역: {city}
자료 수집 작업 단위: {collection_region['name']}
자료 수집 작업 단위에 포함된 행정구역: {', '.join(collection_region['districts'])}

중요한 데이터 의미:
- parentRegionName은 자료 수집 단위가 아니라 사용자가 처음 선택한 큰 지역이다. 모든 결과에 정확히 "{city}"을 넣는다.
- AREA: 성수동·서촌·홍대·연남처럼 도보 또는 짧은 이동으로 활동·식사·카페를 묶을 수 있는 동네·거리·관광 권역.
- HUB: 코엑스·아이파크몰처럼 하나의 복합시설 또는 명확한 대형 명소를 중심으로 활동·식사를 연결할 수 있는 곳.
- 구 전체, 시 전체, 개별 음식점·카페·작은 매장은 후보에서 제외한다.

정제 규칙:
1. 아래 SOURCE에 존재하는 사실만 사용한다. 기억이나 일반 상식으로 사실·좌표·URL을 보충하지 않는다.
2. sourceUrls와 evidence.sourceUrl에는 정확히 SOURCE URL만 사용할 수 있다.
3. sourceQuote에는 후보 존재와 성격을 뒷받침하는 원문의 짧은 구절만 넣는다.
4. 원문에 좌표가 없으면 latitude와 longitude를 null로 둔다.
5. 반경은 원문에 명시되지 않았더라도 AREA 800~2500m, HUB 300~1500m 안에서 보수적인 후보값을 제안할 수 있다. 이 경우 reviewReasons에 "반경 운영 검수 필요"를 넣는다.
6. 좌표가 없거나 후보 성격이 애매하면 REVIEW_NEEDED로 둔다.
7. code는 영문 대문자·숫자·밑줄만 사용하고 50자를 넘지 않는다. 형식은 CITY_AREA_NAME 또는 CITY_HUB_NAME이다.
8. 설명은 서비스 화면에 표시할 수 있는 500자 이하 한글 한 문장이다.
9. 원문이 해당 후보를 뒷받침하지 않으면 빈 anchors 배열을 반환한다.

현재 백엔드에 이미 존재하는 Anchor가 같은 이름으로 발견되면 새 코드를 만들지 말고 기존 code를 사용한다:
{json.dumps(existing_anchors, ensure_ascii=False)}

SOURCE URL: {source['url']}
SOURCE TITLE: {source.get('title') or source.get('label')}
SOURCE TEXT:
{source_text}
""".strip()


def evaluation_prompt(anchor: dict[str, Any], contract: dict[str, Any]) -> str:
    activities = contract["activityCategories"]
    preferences = contract["preferenceGroups"]
    return f"""
목표: 아래 CourseAnchor가 현재 백엔드의 활동 카테고리와 선호도에 얼마나 어울리는지 규칙 후보를 만든다.

CourseAnchor:
{json.dumps(anchor, ensure_ascii=False)}

허용된 활동 카테고리:
{json.dumps(activities, ensure_ascii=False)}

허용된 선호도 그룹·옵션:
{json.dumps(preferences, ensure_ascii=False)}

평가 규칙:
1. Anchor의 description, evidence, sourceUrls에 근거해 명확한 규칙만 생성한다.
2. weight는 5, 10, 15, 20, 25 중 하나다.
   - 5: 약한 보조 적합성
   - 10: 일반적인 적합성
   - 15: 뚜렷한 적합성
   - 20: 강한 대표 적합성
   - 25: 해당 Anchor의 핵심 특성
3. 활동 규칙은 이 권역에서 해당 활동을 코스의 중심으로 구성하기 쉬운 정도다.
4. 선호도 규칙은 활동 점수만으로 충분히 표현되지 않는 Anchor 자체 특성만 넣는다.
5. 같은 의미를 활동 규칙과 선호도 규칙에 기계적으로 중복하지 않는다.
6. COMPANION과 OCCASION은 근거가 매우 명확할 때만 직접 선호도 규칙을 만든다.
7. 모든 허용 항목을 억지로 채우지 않는다. 부적합하거나 근거가 없으면 규칙을 만들지 않는다.
8. confidence는 근거 신뢰도 0~100이다. 60 미만이면 해당 규칙을 반환하지 않는다.
9. reason은 검수자가 판단을 추적할 수 있는 한글 한 문장이다.
10. 허용 목록에 없는 코드는 절대 만들지 않는다.
""".strip()
