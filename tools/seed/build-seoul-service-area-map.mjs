import fs from 'node:fs';
import path from 'node:path';

const [boundaryPath] = process.argv.slice(2);
if (!boundaryPath) throw new Error('Usage: node tools/seed/build-seoul-service-area-map.mjs <법정동-경계.geojson>');

const parseCsv = text => text.trim().split(/\r?\n/).map(line => [...line.matchAll(/(?:^|,)(?:"((?:[^"]|"")*)"|([^,]*))/g)].map(match => (match[1] ?? match[2]).replaceAll('""', '"')));
const seedDir = path.resolve('src/main/resources/db/seed/city/seoul');
const mapping = parseCsv(fs.readFileSync(path.join(seedDir, 'seoul-area-legal-dong-mappings.csv'), 'utf8'));
const [, ...mappingRows] = mapping;
const byCode = new Map(mappingRows.map(([code, fullName, areaCode, areaName]) => [code.slice(0, 8), { code, fullName, areaCode, areaName }]));
const boundaries = JSON.parse(fs.readFileSync(boundaryPath, 'utf8'));

const output = {
  type: 'FeatureCollection',
  metadata: {
    boundarySource: 'https://github.com/southkorea/seoul-maps/tree/master/juso/2015/json',
    mappingSource: 'src/main/resources/db/seed/city/seoul/seoul-area-legal-dong-mappings.csv',
    purpose: '서비스 Area 매핑 검토용 지도. 수집·추천 런타임에는 사용하지 않음.',
  },
  features: boundaries.features.map(feature => {
    const mapping = byCode.get(feature.properties.EMD_CD);
    if (!mapping) throw new Error(`No mapping for boundary ${feature.properties.EMD_CD} ${feature.properties.EMD_KOR_NM}`);
    return {
      type: 'Feature',
      properties: { legalDongCode: mapping.code, legalDongName: mapping.fullName, areaCode: mapping.areaCode, areaName: mapping.areaName },
      geometry: feature.geometry,
    };
  }),
};

if (output.features.length !== 467 || new Set(output.features.map(feature => feature.properties.legalDongCode)).size !== 467) {
  throw new Error(`Expected 467 unique boundaries, got ${output.features.length}`);
}

const outputPath = path.resolve('src/main/resources/static/check-region/seoul-service-area-boundaries.geojson');
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, JSON.stringify(output));
console.log(`${output.features.length} boundaries written to ${outputPath}`);
