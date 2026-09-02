import fs from 'node:fs';
import path from 'node:path';

const seedDir = path.resolve('src/main/resources/db/seed/city/seoul');
const csv = rows => rows.map(row => row.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n') + '\n';
const parseCsv = text => text.trim().split(/\r?\n/).map(line => [...line.matchAll(/(?:^|,)(?:"((?:[^"]|"")*)"|([^,]*))/g)].map(match => (match[1] ?? match[2]).replaceAll('""', '"')));
const columns = header => name => {
  const index = header.indexOf(name);
  if (index < 0) throw new Error(`Missing CSV column: ${name}`);
  return index;
};

const [areaHeader, ...areaRows] = parseCsv(fs.readFileSync(path.join(seedDir, 'seoul-service-areas.csv'), 'utf8'));
const areaColumn = columns(areaHeader);
const areaByCode = new Map(areaRows.filter(row => row[areaColumn('city_code')] === 'SEOUL').map(row => [row[areaColumn('area_code')], row[areaColumn('area_name')]]));
if (areaByCode.size !== 29) throw new Error(`Expected 29 Seoul service areas, got ${areaByCode.size}`);

const mappingPath = path.join(seedDir, 'seoul-area-legal-dong-mappings.csv');
const [mappingHeader, ...mappingRows] = parseCsv(fs.readFileSync(mappingPath, 'utf8'));
const mappingColumn = columns(mappingHeader);
for (const row of mappingRows) {
  const areaName = areaByCode.get(row[mappingColumn('area_code')]);
  if (!areaName) throw new Error(`Unknown area code in mapping: ${row[mappingColumn('area_code')]}`);
  row[mappingColumn('area_name')] = areaName;
}
fs.writeFileSync(mappingPath, csv([mappingHeader, ...mappingRows]));

const mapPath = path.resolve('src/main/resources/static/check-region/seoul-service-area-boundaries.geojson');
const map = JSON.parse(fs.readFileSync(mapPath, 'utf8'));
for (const feature of map.features) {
  const areaName = areaByCode.get(feature.properties.areaCode);
  if (!areaName) throw new Error(`Unknown area code in map: ${feature.properties.areaCode}`);
  feature.properties.areaName = areaName;
}
fs.writeFileSync(mapPath, JSON.stringify(map));

const legalRows = parseCsv(fs.readFileSync(path.join(seedDir, 'seoul-legal-dongs.csv'), 'utf8')).slice(1);
const distribution = [...areaByCode].map(([areaCode, areaName]) => ({ areaName, count: mappingRows.filter(row => row[mappingColumn('area_code')] === areaCode).length }));
if (mappingRows.length !== legalRows.length || new Set(mappingRows.map(row => row[mappingColumn('legal_dong_code')])).size !== mappingRows.length) throw new Error('Legal dong mapping is incomplete or duplicated');
const validation = [
  '# 서울 서비스 Area 법정동 매핑 검증', '',
  `- 기준 법정동: ${legalRows.length}개`, `- 서비스 Area: ${areaByCode.size}개`, '- 미매핑 법정동: 0개', '- 중복 매핑 법정동: 0개', '',
  '## Area별 법정동 수', '', '| Area | 법정동 수 |', '| --- | ---: |', ...distribution.map(row => `| ${row.areaName} | ${row.count} |`), '',
  '## 검토 원칙', '', '- `seoul-service-areas.csv`가 Area 이름의 단일 원본이다.', '- 모든 법정동은 하나의 서비스 Area에만 배정한다.', '- Area 이름 변경 후에는 `make refresh-seoul-area`를 실행한다.',
].join('\n') + '\n';
fs.writeFileSync(path.join(seedDir, 'seoul-area-mapping-validation.md'), validation);
console.log(`Refreshed ${areaByCode.size} service areas across ${mappingRows.length} legal-dong mappings and ${map.features.length} map boundaries.`);
