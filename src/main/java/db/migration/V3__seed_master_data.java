package db.migration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/// CSV 기준의 도시, 태그, 서울 서비스 Area를 초기 적재한다.
public class V3__seed_master_data extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        seedCities(context);
        seedTags(context);
        seedSeoulAreas(context);
        seedSeoulAreaRanks(context);
        seedSeoulAreaTags(context);
    }

    // City는 Area보다 먼저 적재해 외래 키 참조를 보장한다.
    private void seedCities(Context context) throws Exception {
        String sql = "INSERT INTO cities (code, name) VALUES (?, ?) "
                + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name";

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] row : rows("db/seed/city/cities.csv")) {
                statement.setString(1, row[0]);
                statement.setString(2, row[1]);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // 서비스 선택지로 노출할 태그를 적재한다.
    private void seedTags(Context context) throws Exception {
        String sql = "INSERT INTO tags (code, display_name, is_active, display_order) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (code) DO UPDATE SET display_name = EXCLUDED.display_name, "
                + "is_active = EXCLUDED.is_active, display_order = EXCLUDED.display_order";

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] row : rows("db/seed/tags.csv")) {
                statement.setString(1, row[0]);
                statement.setString(2, row[1]);
                statement.setBoolean(3, Boolean.parseBoolean(row[2]));
                statement.setInt(4, Integer.parseInt(row[3]));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // 서울 Area의 이름과 수집 사각형 범위를 함께 적재한다.
    private void seedSeoulAreas(Context context) throws Exception {
        long cityId = findCityId(context, "SEOUL");
        Map<String, String[]> rectangles = rectanglesByAreaCode();
        String sql = "INSERT INTO areas (city_id, code, name, rank, collection_center_latitude, collection_center_longitude, "
                + "collection_min_latitude, collection_min_longitude, collection_max_latitude, collection_max_longitude) "
                + "VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (city_id, code) DO UPDATE SET name = EXCLUDED.name, "
                + "collection_center_latitude = EXCLUDED.collection_center_latitude, "
                + "collection_center_longitude = EXCLUDED.collection_center_longitude, "
                + "collection_min_latitude = EXCLUDED.collection_min_latitude, "
                + "collection_min_longitude = EXCLUDED.collection_min_longitude, "
                + "collection_max_latitude = EXCLUDED.collection_max_latitude, "
                + "collection_max_longitude = EXCLUDED.collection_max_longitude";

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] row : rows("db/seed/city/seoul/seoul-service-areas.csv")) {
                String[] rectangle = rectangles.get(row[1]);
                if (rectangle == null) {
                    throw new IllegalStateException("Area 수집 범위를 찾을 수 없습니다: " + row[1]);
                }
                statement.setLong(1, cityId);
                statement.setString(2, row[1]);
                statement.setString(3, row[2]);
                statement.setBigDecimal(4, new BigDecimal(rectangle[4]));
                statement.setBigDecimal(5, new BigDecimal(rectangle[3]));
                statement.setBigDecimal(6, new BigDecimal(rectangle[6]));
                statement.setBigDecimal(7, new BigDecimal(rectangle[5]));
                statement.setBigDecimal(8, new BigDecimal(rectangle[8]));
                statement.setBigDecimal(9, new BigDecimal(rectangle[7]));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // 서울 전체 순위는 CSV 기준으로 넣고, 순위가 없는 Area는 NULL로 유지한다.
    private void seedSeoulAreaRanks(Context context) throws Exception {
        long cityId = findCityId(context, "SEOUL");
        String sql = "UPDATE areas SET rank = ? WHERE city_id = ? AND code = ?";
        int count = 0;

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] row : rows("db/seed/city/seoul/seoul-area-ranks.csv")) {
                if (row[2].isBlank()) {
                    statement.setNull(1, Types.INTEGER);
                } else {
                    statement.setInt(1, Integer.parseInt(row[2]));
                }
                statement.setLong(2, cityId);
                statement.setString(3, row[1]);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("순위를 반영할 서울 Area가 없습니다: " + row[1]);
                }
                count++;
            }
        }
        if (count != 29) {
            throw new IllegalStateException("서울 Area 순위 seed 행 수가 올바르지 않습니다: " + count);
        }
    }

    // 조사 CSV 세 개를 Area·Tag 키로 연결해 AreaTag 순위와 설명을 적재한다.
    private void seedSeoulAreaTags(Context context) throws Exception {
        long cityId = findCityId(context, "SEOUL");
        Map<String, Long> areaIds = idsByCode(context,
                "SELECT code, id FROM areas WHERE city_id = ?", cityId);
        Map<String, Long> tagIds = idsByCode(context,
                "SELECT code, id FROM tags", null);
        String sql = "INSERT INTO area_tags (area_id, tag_id, weight, recommendation_rank, "
                + "recommend_visit_places, attraction_features, differentiators) "
                + "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb) "
                + "ON CONFLICT (area_id, tag_id) DO UPDATE SET weight = EXCLUDED.weight, "
                + "recommendation_rank = EXCLUDED.recommendation_rank, "
                + "recommend_visit_places = EXCLUDED.recommend_visit_places, "
                + "attraction_features = EXCLUDED.attraction_features, "
                + "differentiators = EXCLUDED.differentiators";
        int count = 0;

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] row : rows("db/seed/city/seoul/seoul-area-tags.csv")) {
                Long areaId = areaIds.get(row[1]);
                Long tagId = tagIds.get(row[2]);
                if (areaId == null || tagId == null) {
                    throw new IllegalStateException("AreaTag seed에서 Area 또는 Tag를 찾을 수 없습니다: "
                            + row[1] + "/" + row[2]);
                }
                statement.setLong(1, areaId);
                statement.setLong(2, tagId);
                statement.setInt(3, Integer.parseInt(row[3]));
                statement.setInt(4, Integer.parseInt(row[4]));
                statement.setString(5, row[5]);
                statement.setString(6, row[6]);
                statement.setString(7, row[7]);
                statement.addBatch();
                count++;
            }
            statement.executeBatch();
        }
        if (count != 288) {
            throw new IllegalStateException("서울 AreaTag seed 행 수가 올바르지 않습니다: " + count);
        }
    }

    // 필요한 키 목록을 한 번에 읽어 seed 동안 반복 조회하지 않도록 한다.
    private Map<String, Long> idsByCode(Context context, String sql, Long cityId) throws Exception {
        Map<String, Long> ids = new HashMap<>();
        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            if (cityId != null) {
                statement.setLong(1, cityId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.put(resultSet.getString("code"), resultSet.getLong("id"));
                }
            }
        }
        return ids;
    }

    // 서울 Area와 수집 범위 CSV의 코드가 일치하는지 확인한다.
    private Map<String, String[]> rectanglesByAreaCode() throws Exception {
        Map<String, String[]> rectangles = new HashMap<>();
        for (String[] row : rows("db/seed/city/seoul/seoul-area-collection-rectangles.csv")) {
            rectangles.put(row[1], row);
        }
        if (rectangles.size() != 29) {
            throw new IllegalStateException("서울 Area 수집 범위가 완전하지 않습니다: " + rectangles.size());
        }
        return rectangles;
    }

    // Area가 반드시 기존 City에 연결되도록 한다.
    private long findCityId(Context context, String cityCode) throws Exception {
        try (PreparedStatement statement = context.getConnection()
                .prepareStatement("SELECT id FROM cities WHERE code = ?")) {
            statement.setString(1, cityCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("도시 시드를 찾을 수 없습니다: " + cityCode);
                }
                return resultSet.getLong(1);
            }
        }
    }

    // 필요한 수준의 CSV 따옴표만 처리해 시드 파일을 읽는다.
    private Iterable<String[]> rows(String resource) throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("CSV seed를 찾을 수 없습니다: " + resource);
        }

        List<String[]> rows = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(parseCsv(line));
                }
            }
        }
        return rows;
    }

    // Area 이름에 쉼표가 포함되어도 컬럼 순서를 유지한다.
    private String[] parseCsv(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                columns.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        columns.add(value.toString());
        return columns.toArray(String[]::new);
    }
}
