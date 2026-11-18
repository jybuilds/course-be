"""기존 조사 CSV를 보존하고 지역·태그별 읽기용 목록만 생성한다."""
import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = Path(__file__).resolve().parents[4] / 'src/main/resources/db/seed/city/seoul/seoul-area-tags.csv'

def read(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))

anchors = read(ROOT / 'anchors.csv')
links = read(ROOT / 'area_tag_anchors.csv')
reviews = read(ROOT / 'area_tag_reviews.csv')
progress = read(ROOT / 'progress.csv')
inputs = read(SEED)
lookup = {row['anchor_id']: row for row in anchors}
sources = {row['source_id'] for row in read(ROOT / 'sources.csv')}
tag_names = {
    'CAFE': '카페', 'SHOPPING': '쇼핑', 'MARKET': '시장 구경',
    'EXHIBITION': '전시', 'POPUP': '팝업', 'BOOKSTORE': '서점',
    'WORKSHOP': '공방', 'PERFORMANCE': '공연', 'WALK': '산책',
    'PARK': '공원', 'SCENIC_VIEW': '경관·전망',
    'TRENDY': '힙한 공간', 'LIVELY': '활기찬 거리',
}
input_keys = {(row['area_code'], row['tag_code']) for row in inputs}
review_keys = {(row['area_code'], row['tag_code']) for row in reviews}
assert len(lookup) == len(anchors), 'Duplicate anchor ID'
assert len({(r['area_code'], r['tag_code'], r['anchor_id']) for r in links}) == len(links)
assert len(input_keys) == len(inputs), 'Duplicate input AreaTag'
assert review_keys <= input_keys, 'Unexpected review AreaTag'
assert all(row['tag_code'] in tag_names for row in inputs)
assert {row['anchor_id'] for row in links} == set(lookup), 'Unlinked anchor'
for row in anchors:
    assert row['anchor_type'] in {'VENUE', 'STREET', 'DISTRICT', 'ROUTE'}
    parent_id = row['parent_anchor_id']
    if parent_id:
        assert parent_id != row['anchor_id']
        assert lookup[parent_id]['area_code'] == row['area_code']
for row in links:
    assert (row['area_code'], row['tag_code']) in input_keys
    assert lookup[row['anchor_id']]['area_code'] == row['area_code']
    evidence = json.loads(row['evidence_ids'])
    assert isinstance(evidence, list) and evidence
    assert all(s in sources for s in evidence)
for row in reviews:
    assert row['decision'] in {'SELECTED', 'HOLD', 'REJECTED'}
    assert all(s in sources for s in json.loads(row['evidence_ids']))
for row in progress:
    if row['status'] == 'COMPLETED':
        expected = {key for key in input_keys if key[0] == row['area_code']}
        assert expected <= review_keys, 'Completed Area has unreviewed tags'
        assert int(row['reviewed_tag_count']) == len(expected)
        assert int(row['selected_anchor_count']) == sum(a['area_code'] == row['area_code'] for a in anchors)

lines = ['# 서울 지역별·태그별 앵커 목록', '',
         '2026-11-07 기준 조사 결과. 지역 전체 목록은 중복 제거했고, 아래 표는 각 지역에 이미 붙은 태그별 방문 목적 앵커입니다.', '',
         '보류는 장소가 없다는 뜻이 아니라 채택 근거가 부족하다는 뜻입니다. 종료된 팝업·전시는 현재 행사로 안내하지 않습니다.', '',
         '[조사 기준·한계](README.md) · [선정 관계 CSV](area_tag_anchors.csv) · [앵커 CSV](anchors.csv) · [출처 CSV](sources.csv)', '']
for area in progress:
    code = area['area_code']
    relations = [r for r in links if r['area_code'] == code]
    names = list(dict.fromkeys(lookup[r['anchor_id']]['anchor_name'] for r in relations))
    status = '검토 완료' if area['status'] == 'COMPLETED' else area['status']
    lines += ['## ' + area['area_name'], '',
              '조사 상태: ' + status + ' · [상세 조사 기록](areas/' + code + '.md)', '',
              '**지역 전체 앵커:** ' + (' · '.join(names) or '아직 선정되지 않음'), '', '| 태그 | 앵커 |', '|---|---|']
    for tag in [r['tag_code'] for r in inputs if r['area_code'] == code]:
        matched = list(dict.fromkeys(lookup[r['anchor_id']]['anchor_name'] for r in relations if r['tag_code'] == tag))
        checked = any(r['area_code'] == code and r['tag_code'] == tag for r in reviews)
        value = ' · '.join(matched) if matched else ('선정 보류 — 후보 검증 기록 참고' if checked else '조사 대기·진행 중')
        lines.append('| ' + tag_names[tag] + ' (`' + tag + '`) | ' + value + ' |')
    lines.append('')
(ROOT / 'ANCHORS-BY-AREA.md').write_text('\n'.join(lines), encoding='utf-8')
assert len(inputs) == sum(1 for line in lines if line.startswith('| ') and not line.startswith('| 태그'))
print(f'{len(progress)}개 지역, {len(inputs)}개 태그 관계 표시; 앵커·출처 참조 검증 통과')
