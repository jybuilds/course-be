import fs from 'node:fs';
import path from 'node:path';

const input = process.argv[2];
const outputDir = path.resolve('src/main/resources/db/seed/city/seoul');

if (!input) throw new Error('Usage: node tools/seed/build-seoul-service-area-seed.mjs <법정동코드-utf8.tsv>');

const parseCsv = text => text.trim().split(/\r?\n/).map(line => [...line.matchAll(/(?:^|,)(?:"((?:[^"]|"")*)"|([^,]*))/g)].map(match => (match[1] ?? match[2]).replaceAll('""', '"')));
const [areaHeader, ...areaRows] = parseCsv(fs.readFileSync(path.join(outputDir, 'seoul-service-areas.csv'), 'utf8'));
const column = name => areaHeader.indexOf(name);
const cityCodeIndex = column('city_code');
const areaCodeIndex = column('area_code');
const areaNameIndex = column('area_name');
if ([cityCodeIndex, areaCodeIndex, areaNameIndex].some(index => index < 0)) throw new Error('seoul-service-areas.csv requires city_code, area_code, area_name columns');

const areas = areaRows.filter(row => row[cityCodeIndex] === 'SEOUL').map(row => [row[areaCodeIndex], row[areaNameIndex]]);
const byArea = new Map(areas);
if (areas.length !== 29 || byArea.size !== areas.length) throw new Error(`Expected 29 unique Seoul service areas, got ${areas.length}`);
const areaForDistrict = {
  '강북구': 'SEOUL_BUKHANSAN_GANGBUK', '성북구': 'SEOUL_BUKHANSAN_GANGBUK',
  '도봉구': 'SEOUL_DOBONG_NOWON', '노원구': 'SEOUL_DOBONG_NOWON',
  '은평구': 'SEOUL_EUNPYEONG_YEONHUI', '서대문구': 'SEOUL_EUNPYEONG_YEONHUI',
  '중구': 'SEOUL_MYEONGDONG_EULJIRO', '마포구': 'SEOUL_MAPO_GONGDEOK',
  '성동구': 'SEOUL_WANGSIMNI_KONKUK', '광진구': 'SEOUL_WANGSIMNI_KONKUK',
  '동대문구': 'SEOUL_CHEONGNYANGNI_JUNGNANG', '중랑구': 'SEOUL_CHEONGNYANGNI_JUNGNANG',
  '용산구': 'SEOUL_YONGSAN', '영등포구': 'SEOUL_YEONGDEUNGPO_MULLAE',
  '양천구': 'SEOUL_MOKDONG_YANGCHEON', '강서구': 'SEOUL_GANGSEO_MAGOK',
  '구로구': 'SEOUL_GURO_GEUMCHEON', '금천구': 'SEOUL_GURO_GEUMCHEON',
  '동작구': 'SEOUL_GWANAK_SADANG', '관악구': 'SEOUL_GWANAK_SADANG',
  '서초구': 'SEOUL_SEOCHO_BANPO', '강남구': 'SEOUL_GANGNAM_YEOKSAM',
  '송파구': 'SEOUL_JAMSIL_SONGPA', '강동구': 'SEOUL_GANGDONG',
};

const overrides = new Map();
const set = (district, names, areaCode) => names.forEach(name => overrides.set(`${district}/${name}`, areaCode));

set('종로구', ['청운동', '신교동', '궁정동', '효자동', '창성동', '통의동', '적선동', '통인동', '누상동', '누하동', '옥인동', '체부동', '필운동', '내자동', '사직동', '도렴동', '당주동', '내수동', '세종로', '신문로1가', '신문로2가', '청진동', '서린동', '수송동', '중학동'], 'SEOUL_GWANGHWAMUN_SEOCHON');
set('종로구', ['이화동', '연건동', '충신동', '동숭동', '혜화동', '명륜1가', '명륜2가', '명륜3가', '명륜4가', '창신동', '숭인동'], 'SEOUL_DAEHAKRO_DONGDAEMUN');
set('종로구', ['구기동', '평창동', '부암동', '홍지동', '신영동', '무악동'], 'SEOUL_BUKHANSAN_GANGBUK');
set('서대문구', ['대현동', '대신동', '신촌동', '봉원동', '창천동', '북아현동'], 'SEOUL_SINCHON_EWHA');
set('서대문구', ['충정로2가', '충정로3가', '합동', '미근동', '냉천동', '천연동', '옥천동', '영천동', '현저동'], 'SEOUL_SEOUL_STATION_NAMDAEMUN');
set('중구', ['남대문로1가', '남대문로2가', '남대문로3가', '남대문로4가', '남대문로5가', '남창동', '북창동', '봉래동1가', '봉래동2가', '회현동1가', '회현동2가', '회현동3가', '서소문동', '정동', '순화동', '의주로1가', '의주로2가', '중림동', '만리동1가', '만리동2가'], 'SEOUL_SEOUL_STATION_NAMDAEMUN');
set('마포구', ['서교동', '동교동', '연남동'], 'SEOUL_HONGDAE_YEONNAM');
set('마포구', ['상수동', '하중동', '신정동', '당인동', '합정동', '망원동'], 'SEOUL_SANGSU_HAPJEONG_MANGWON');
set('성동구', ['성수동1가', '성수동2가', '송정동'], 'SEOUL_SEONGSU');
set('용산구', ['이태원동', '한남동', '동빙고동', '서빙고동', '주성동', '용산동6가', '보광동'], 'SEOUL_ITAEWON_HANNAM');
set('영등포구', ['여의도동'], 'SEOUL_YEOUIDO');
set('강남구', ['청담동', '압구정동', '신사동', '논현동'], 'SEOUL_APGUJEONG_CHEONGDAM');
set('강남구', ['삼성동', '대치동'], 'SEOUL_SAMSUNG_COEX');

const rows = fs.readFileSync(input, 'utf8').trim().split(/\r?\n/).slice(1)
  .map(line => line.split('\t'))
  .filter(([code, name, status]) => code.startsWith('11') && status === '존재' && code.slice(5) !== '00000')
  .map(([code, fullName]) => {
    const [, districtName, legalDongName] = fullName.split(' ');
    const areaCode = overrides.get(`${districtName}/${legalDongName}`) ?? areaForDistrict[districtName] ?? 'SEOUL_BUKCHON_INSADONG_IKSUN';
    if (!byArea.has(areaCode)) throw new Error(`Unknown area for ${fullName}: ${areaCode}`);
    return { code, districtName, legalDongName, fullName, areaCode };
  });

if (rows.length !== 467) throw new Error(`Expected 467 active Seoul legal dongs, got ${rows.length}`);
if (new Set(rows.map(row => row.code)).size !== rows.length) throw new Error('Duplicate legal dong code detected');

const csv = rows => rows.map(row => row.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n') + '\n';
const source = 'https://www.code.go.kr/stdcode/regCodeL.do?menuNo=101010100010';
const sourceUpdatedAt = '2026-07-08';
const dongRows = rows.map(row => [row.code, '서울특별시', row.districtName, row.legalDongName, row.fullName, source, sourceUpdatedAt]);
const mappingRows = rows.map(row => [row.code, row.fullName, row.areaCode, byArea.get(row.areaCode), '법정동명 기반 서비스 Area 매핑', 'REVIEW_REQUIRED']);

fs.mkdirSync(outputDir, { recursive: true });
fs.writeFileSync(path.join(outputDir, 'seoul-legal-dongs.csv'), csv([['legal_dong_code', 'city_name', 'district_name', 'legal_dong_name', 'legal_dong_full_name', 'source', 'source_updated_at'], ...dongRows]));
fs.writeFileSync(path.join(outputDir, 'seoul-area-legal-dong-mappings.csv'), csv([['legal_dong_code', 'legal_dong_full_name', 'area_code', 'area_name', 'mapping_basis', 'review_status'], ...mappingRows]));

const distribution = areas.map(([areaCode, areaName]) => ({ areaCode, areaName, count: rows.filter(row => row.areaCode === areaCode).length }));
const validation = [
  '# 서울 서비스 Area 법정동 매핑 검증',
  '',
  `- 기준 법정동: ${rows.length}개`,
  `- 서비스 Area: ${areas.length}개`,
  `- 미매핑 법정동: 0개`,
  `- 중복 매핑 법정동: 0개`,
  `- 원본: ${source}`,
  `- 원본 기준일: ${sourceUpdatedAt}`,
  '',
  '## Area별 법정동 수',
  '',
  '| Area | 법정동 수 |',
  '| --- | ---: |',
  ...distribution.map(row => `| ${row.areaName} | ${row.count} |`),
  '',
  '## 검토 원칙',
  '',
  '- 모든 법정동은 하나의 서비스 Area에만 배정한다.',
  '- 법정동이 실제 생활권 경계에 걸리는 경우에도 MVP에서는 하나의 대표 Area를 지정한다.',
  '- `REVIEW_REQUIRED` 행은 서비스 기획 확정 시 이름과 소속을 조정한다.',
].join('\n') + '\n';
fs.writeFileSync(path.join(outputDir, 'seoul-area-mapping-validation.md'), validation);

console.log(JSON.stringify({ legalDongCount: rows.length, areaCount: areas.length, distribution }, null, 2));
