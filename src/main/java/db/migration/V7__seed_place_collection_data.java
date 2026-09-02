package db.migration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/// CSV 기준의 장소 수집 프로필과 PlaceType 보정 규칙을 초기 적재한다.
public class V7__seed_place_collection_data extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        seedCollectionProfiles(context);
        seedCategoryRules(context);
    }

    // 카테고리·키워드 기반 카카오 수집 프로필을 CSV 기준으로 적재한다.
    private void seedCollectionProfiles(Context context) throws Exception {
        String sql = "INSERT INTO place_collection_profiles "
                + "(provider, code, name, search_type, category_group_code, query, place_type, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (provider, code) DO UPDATE SET "
                + "name = EXCLUDED.name, search_type = EXCLUDED.search_type, "
                + "category_group_code = EXCLUDED.category_group_code, query = EXCLUDED.query, "
                + "place_type = EXCLUDED.place_type, is_active = EXCLUDED.is_active";

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] row : rows("db/seed/place_collection_profiles.csv")) {
                statement.setString(1, row[0]);
                statement.setString(2, row[1]);
                statement.setString(3, row[2]);
                statement.setString(4, row[3]);
                statement.setString(5, nullIfBlank(row[4]));
                statement.setString(6, nullIfBlank(row[5]));
                statement.setString(7, row[6]);
                statement.setBoolean(8, Boolean.parseBoolean(row[7]));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // 장소명·카카오·네이버 카테고리에 공통 적용할 타입 보정 규칙을 CSV 기준으로 적재한다.
    private void seedCategoryRules(Context context) throws Exception {
        String sql = "INSERT INTO place_category_rules (keyword, target_place_type, priority, is_active) "
                + "VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (keyword) DO UPDATE SET target_place_type = EXCLUDED.target_place_type, "
                + "priority = EXCLUDED.priority, is_active = EXCLUDED.is_active";

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] row : rows("db/seed/place_category_rules.csv")) {
                statement.setString(1, row[0]);
                statement.setString(2, row[1]);
                statement.setInt(3, Integer.parseInt(row[2]));
                statement.setBoolean(4, Boolean.parseBoolean(row[3]));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // 빈 CSV 값은 PostgreSQL NULL로 저장해 검색 방식 제약조건을 만족시킨다.
    private String nullIfBlank(String value) {
        return value.isBlank() ? null : value;
    }

    // 수집 프로필 시드는 쉼표가 없는 단순 CSV 형식으로 읽는다.
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
                    rows.add(line.split(",", -1));
                }
            }
        }
        return rows;
    }
}
