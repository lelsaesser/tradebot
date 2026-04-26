package org.tradelite.repository;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tradelite.common.OhlcvRecord;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteOhlcvRepository implements OhlcvRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void saveAll(List<OhlcvRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        String sql =
                """
                INSERT OR REPLACE INTO twelvedata_daily_ohlcv
                (symbol, date, open, high, low, close, volume)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(
                sql,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i)
                            throws java.sql.SQLException {
                        OhlcvRecord ohlcv = records.get(i);
                        ps.setString(1, ohlcv.symbol());
                        ps.setString(2, ohlcv.date().toString());
                        ps.setDouble(3, ohlcv.open());
                        ps.setDouble(4, ohlcv.high());
                        ps.setDouble(5, ohlcv.low());
                        ps.setDouble(6, ohlcv.close());
                        ps.setLong(7, ohlcv.volume());
                    }

                    @Override
                    public int getBatchSize() {
                        return records.size();
                    }
                });
        log.debug("Saved {} OHLCV records", records.size());
    }

    @Override
    public List<OhlcvRecord> findBySymbol(String symbol, int days) {
        String sql =
                """
                SELECT symbol, date, open, high, low, close, volume
                FROM twelvedata_daily_ohlcv
                WHERE symbol = ? AND date >= date('now', ?)
                ORDER BY date ASC
                """;

        return jdbcTemplate.query(sql, this::mapResultSetToRecord, symbol, "-" + days + " days");
    }

    private OhlcvRecord mapResultSetToRecord(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new OhlcvRecord(
                rs.getString("symbol"),
                LocalDate.parse(rs.getString("date")),
                rs.getDouble("open"),
                rs.getDouble("high"),
                rs.getDouble("low"),
                rs.getDouble("close"),
                rs.getLong("volume"));
    }

    @Override
    public int deleteBySymbol(String symbol) {
        String sql = "DELETE FROM twelvedata_daily_ohlcv WHERE symbol = ?";
        int deleted = jdbcTemplate.update(sql, symbol);
        log.info("Deleted {} OHLCV records for symbol {}", deleted, symbol);
        return deleted;
    }
}
