package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;

/// CSV 기준 데이터를 데이터베이스에 적재한다.
public class V5__seed_master_data extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        seedCities(context);
        seedTags(context);
        seedTagOptions(context);
    }

    private void seedCities(Context context) throws Exception {
        String sql = "INSERT INTO cities (code, name) VALUES (?, ?) "
                + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name";
        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] row : rows("db/seed/cities.csv")) {
                statement.setString(1, row[0]);
                statement.setString(2, row[1]);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

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

    private void seedTagOptions(Context context) throws Exception {
        String sql = "INSERT INTO tag_options (tag_id, option_name, display_order, is_active) "
                + "VALUES ((SELECT id FROM tags WHERE code = ?), ?, ?, ?) "
                + "ON CONFLICT (tag_id, option_name) DO UPDATE SET display_order = EXCLUDED.display_order, "
                + "is_active = EXCLUDED.is_active";
        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] row : rows("db/seed/tag_options.csv")) {
                statement.setString(1, row[0]);
                statement.setString(2, row[1]);
                statement.setInt(3, Integer.parseInt(row[2]));
                statement.setBoolean(4, Boolean.parseBoolean(row[3]));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Iterable<String[]> rows(String resource) throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("CSV seed를 찾을 수 없습니다: " + resource);
        }
        var rows = new java.util.ArrayList<String[]>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) rows.add(line.split(",", -1));
            }
        }
        return rows;
    }
}
