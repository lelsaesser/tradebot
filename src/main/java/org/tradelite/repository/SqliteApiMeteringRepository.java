package org.tradelite.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteApiMeteringRepository implements ApiMeteringRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void saveAll(List<ApiMeteringRecord> records) {
        String sql =
                """
                INSERT OR REPLACE INTO api_request_metering
                (provider, month, count, last_updated)
                VALUES (?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(
                sql,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NonNull PreparedStatement ps, int i)
                            throws SQLException {
                        ApiMeteringRecord meteringRecord = records.get(i);
                        ps.setString(1, meteringRecord.provider());
                        ps.setString(2, meteringRecord.month());
                        ps.setInt(3, meteringRecord.count());
                        ps.setString(4, meteringRecord.lastUpdated().toString());
                    }

                    @Override
                    public int getBatchSize() {
                        return records.size();
                    }
                });

        log.debug("Flushed {} API metering records to database", records.size());
    }

    @Override
    public List<ApiMeteringRecord> findAll() {
        String sql =
                """
                SELECT provider, month, count, last_updated
                FROM api_request_metering
                """;

        return jdbcTemplate.query(sql, this::mapRow);
    }

    private ApiMeteringRecord mapRow(java.sql.ResultSet rs, int rowNum) throws SQLException {
        return new ApiMeteringRecord(
                rs.getString("provider"),
                rs.getString("month"),
                rs.getInt("count"),
                LocalDateTime.parse(rs.getString("last_updated")));
    }
}
