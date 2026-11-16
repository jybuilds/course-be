package com.junsang.course_backend.domain.place.collection.budget;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class CollectionBudgetWriter {
    private final JdbcTemplate jdbc;

    public record Block(Instant until, String reason) {
        public boolean active(Instant now) {
            return until == null || until.isAfter(now);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Block> find(ApiScope scope, String credentialId) {
        return jdbc.query("SELECT blocked_until, reason FROM collection_credential_blocks WHERE api_scope=? AND credential_id=?",
                (rs, row) -> new Block(rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), rs.getString(2)),
                scope.name(), credentialId).stream().findFirst();
    }

    @Transactional
    public void block(ApiScope scope, String credentialId, Instant until, String reason) {
        jdbc.update("""
                INSERT INTO collection_credential_blocks(api_scope,credential_id,blocked_until,reason)
                VALUES (?,?,?,?) ON CONFLICT(api_scope,credential_id) DO UPDATE
                SET blocked_until=EXCLUDED.blocked_until, reason=EXCLUDED.reason, updated_at=CURRENT_TIMESTAMP
                """, scope.name(), credentialId, until == null ? null : Timestamp.from(until), reason);
    }
}
