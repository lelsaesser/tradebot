package org.tradelite.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.tradelite.client.finviz.dto.IndustryPerformance;
import org.tradelite.core.SectorPerformanceSnapshot;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteSectorPerformanceRepository implements SectorPerformanceRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void saveSnapshot(SectorPerformanceSnapshot snapshot) {
        String fetchDate = snapshot.fetchDate().toString();
        jdbcTemplate.update("DELETE FROM industry_performance WHERE fetch_date = ?", fetchDate);

        List<IndustryPerformance> performances = snapshot.performances();
        if (performances.isEmpty()) {
            return;
        }

        String sql =
                """
                INSERT INTO industry_performance
                (fetch_date, industry_name, daily_change, weekly_perf, monthly_perf,
                 quarterly_perf, half_year_perf, yearly_perf, ytd_perf)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(
                sql,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NonNull PreparedStatement ps, int i)
                            throws SQLException {
                        IndustryPerformance perf = performances.get(i);
                        ps.setString(1, fetchDate);
                        ps.setString(2, perf.name());
                        setNullableDouble(ps, 3, perf.change());
                        setNullableDouble(ps, 4, perf.perfWeek());
                        setNullableDouble(ps, 5, perf.perfMonth());
                        setNullableDouble(ps, 6, perf.perfQuarter());
                        setNullableDouble(ps, 7, perf.perfHalf());
                        setNullableDouble(ps, 8, perf.perfYear());
                        setNullableDouble(ps, 9, perf.perfYtd());
                    }

                    @Override
                    public int getBatchSize() {
                        return performances.size();
                    }
                });
        log.debug(
                "Saved sector performance snapshot for {} with {} industries",
                fetchDate,
                performances.size());
    }

    @Override
    public List<SectorPerformanceSnapshot> findSnapshotsSince(LocalDate since) {
        String sql =
                """
                SELECT fetch_date, industry_name, daily_change, weekly_perf, monthly_perf,
                       quarterly_perf, half_year_perf, yearly_perf, ytd_perf
                FROM industry_performance
                WHERE fetch_date >= ?
                ORDER BY fetch_date, industry_name
                """;

        List<IndustryPerformanceRow> rows = jdbcTemplate.query(sql, this::mapRow, since.toString());

        Map<LocalDate, List<IndustryPerformance>> grouped = new LinkedHashMap<>();
        for (IndustryPerformanceRow row : rows) {
            grouped.computeIfAbsent(row.fetchDate, _ -> new ArrayList<>()).add(row.performance);
        }

        return grouped.entrySet().stream()
                .map(e -> new SectorPerformanceSnapshot(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public Optional<SectorPerformanceSnapshot> findLatestSnapshot() {
        String sql =
                """
                SELECT fetch_date, industry_name, daily_change, weekly_perf, monthly_perf,
                       quarterly_perf, half_year_perf, yearly_perf, ytd_perf
                FROM industry_performance
                WHERE fetch_date = (SELECT MAX(fetch_date) FROM industry_performance)
                ORDER BY industry_name
                """;

        List<IndustryPerformanceRow> rows = jdbcTemplate.query(sql, this::mapRow);
        return buildSnapshot(rows);
    }

    @Override
    public Optional<SectorPerformanceSnapshot> findByDate(LocalDate date) {
        String sql =
                """
                SELECT fetch_date, industry_name, daily_change, weekly_perf, monthly_perf,
                       quarterly_perf, half_year_perf, yearly_perf, ytd_perf
                FROM industry_performance
                WHERE fetch_date = ?
                ORDER BY industry_name
                """;

        List<IndustryPerformanceRow> rows = jdbcTemplate.query(sql, this::mapRow, date.toString());
        return buildSnapshot(rows);
    }

    private Optional<SectorPerformanceSnapshot> buildSnapshot(List<IndustryPerformanceRow> rows) {
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        LocalDate fetchDate = rows.getFirst().fetchDate;
        List<IndustryPerformance> performances = rows.stream().map(r -> r.performance).toList();
        return Optional.of(new SectorPerformanceSnapshot(fetchDate, performances));
    }

    private IndustryPerformanceRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        LocalDate fetchDate = LocalDate.parse(rs.getString("fetch_date"));
        IndustryPerformance performance =
                new IndustryPerformance(
                        rs.getString("industry_name"),
                        getNullableBigDecimal(rs, "weekly_perf"),
                        getNullableBigDecimal(rs, "monthly_perf"),
                        getNullableBigDecimal(rs, "quarterly_perf"),
                        getNullableBigDecimal(rs, "half_year_perf"),
                        getNullableBigDecimal(rs, "yearly_perf"),
                        getNullableBigDecimal(rs, "ytd_perf"),
                        getNullableBigDecimal(rs, "daily_change"));
        return new IndustryPerformanceRow(fetchDate, performance);
    }

    private static BigDecimal getNullableBigDecimal(ResultSet rs, String column)
            throws SQLException {
        Object value = rs.getObject(column);
        return value != null ? BigDecimal.valueOf(rs.getDouble(column)) : null;
    }

    private static void setNullableDouble(PreparedStatement ps, int index, BigDecimal value)
            throws SQLException {
        if (value != null) {
            ps.setDouble(index, value.doubleValue());
        } else {
            ps.setObject(index, null);
        }
    }

    private record IndustryPerformanceRow(LocalDate fetchDate, IndustryPerformance performance) {}
}
