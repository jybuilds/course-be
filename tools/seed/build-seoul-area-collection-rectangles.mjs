import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve('.');
const seedDir = path.join(root, 'src/main/resources/db/seed/city/seoul');
const boundaryPath = path.join(root, 'src/main/resources/static/check-region/seoul-service-area-boundaries.geojson');
const csvPath = path.join(seedDir, 'seoul-area-collection-rectangles.csv');
const geoJsonPath = path.join(root, 'src/main/resources/static/check-region/seoul-area-collection-rectangles.geojson');

const csv = rows => rows.map(row => row.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n') + '\n';
const parseCsv = text => text.trim().split(/\r?\n/).map(line => [...line.matchAll(/(?:^|,)(?:"((?:[^"]|"")*)"|([^,]*))/g)].map(match => (match[1] ?? match[2]).replaceAll('""', '"')));

const [areaHeader, ...areaRows] = parseCsv(fs.readFileSync(path.join(seedDir, 'seoul-service-areas.csv'), 'utf8'));
const column = name => {
  const index = areaHeader.indexOf(name);
  if (index < 0) throw new Error(`Missing area column: ${name}`);
  return index;
};
const areaCodeIndex = column('area_code');
const areaNameIndex = column('area_name');
const areas = new Map(areaRows.map(row => [row[areaCodeIndex], row[areaNameIndex]]));
if (areas.size !== 29) throw new Error(`Expected 29 service areas, got ${areas.size}`);

const boundsByArea = new Map([...areas.keys()].map(areaCode => [areaCode, {
  minLongitude: Infinity,
  minLatitude: Infinity,
  maxLongitude: -Infinity,
  maxLatitude: -Infinity,
}]));
const collectCoordinates = (coordinates, visit) => {
  if (typeof coordinates[0] === 'number') {
    visit(coordinates[0], coordinates[1]);
    return;
  }
  coordinates.forEach(value => collectCoordinates(value, visit));
};

const boundaries = JSON.parse(fs.readFileSync(boundaryPath, 'utf8'));
for (const feature of boundaries.features) {
  const bounds = boundsByArea.get(feature.properties.areaCode);
  if (!bounds) throw new Error(`Unknown area in boundary map: ${feature.properties.areaCode}`);
  collectCoordinates(feature.geometry.coordinates, (longitude, latitude) => {
    bounds.minLongitude = Math.min(bounds.minLongitude, longitude);
    bounds.minLatitude = Math.min(bounds.minLatitude, latitude);
    bounds.maxLongitude = Math.max(bounds.maxLongitude, longitude);
    bounds.maxLatitude = Math.max(bounds.maxLatitude, latitude);
  });
}

const rows = [...areas].map(([areaCode, areaName]) => {
  const bounds = boundsByArea.get(areaCode);
  if (!Number.isFinite(bounds.minLongitude)) throw new Error(`No boundary found for ${areaCode}`);
  return [
    'SEOUL', areaCode, areaName,
    ((bounds.minLongitude + bounds.maxLongitude) / 2).toFixed(7),
    ((bounds.minLatitude + bounds.maxLatitude) / 2).toFixed(7),
    bounds.minLongitude.toFixed(7), bounds.minLatitude.toFixed(7),
    bounds.maxLongitude.toFixed(7), bounds.maxLatitude.toFixed(7),
    'LEGAL_DONG_BOUNDARY_BBOX',
    '법정동 경계를 모두 감싸는 초기 Kakao rect 검색 범위. Place Area 배정에는 사용하지 않음.',
  ];
});
fs.writeFileSync(csvPath, csv([[
  'city_code', 'area_code', 'area_name',
  'center_longitude', 'center_latitude',
  'min_longitude', 'min_latitude', 'max_longitude', 'max_latitude',
  'calculation_method', 'notes',
], ...rows]));

const features = rows.map(([cityCode, areaCode, areaName, centerLongitude, centerLatitude, minLongitude, minLatitude, maxLongitude, maxLatitude, calculationMethod, notes]) => ({
  type: 'Feature',
  properties: { cityCode, areaCode, areaName, centerLongitude, centerLatitude, calculationMethod, notes },
  geometry: {
    type: 'Polygon',
    coordinates: [[
      [Number(minLongitude), Number(minLatitude)],
      [Number(maxLongitude), Number(minLatitude)],
      [Number(maxLongitude), Number(maxLatitude)],
      [Number(minLongitude), Number(maxLatitude)],
      [Number(minLongitude), Number(minLatitude)],
    ]],
  },
}));
fs.writeFileSync(geoJsonPath, JSON.stringify({
  type: 'FeatureCollection',
  metadata: {
    purpose: 'Kakao 초기 수집 범위 검토용. Place Area 배정에는 사용하지 않음.',
    source: 'seoul-service-area-boundaries.geojson',
  },
  features,
}));

if (rows.length !== 29 || new Set(rows.map(row => row[1])).size !== 29) throw new Error('Collection rectangle generation is incomplete');
console.log(`${rows.length} collection rectangles written to ${csvPath}`);
