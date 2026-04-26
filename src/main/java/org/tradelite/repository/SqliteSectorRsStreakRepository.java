package org.tradelite.repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tradelite.core.SectorRsStreak;

/**
 * SQLite implementation of {@link SectorRsStreakRepository}.
 *
 * <p>Stores sector RS streak data (consecutive days of outperformance/underperformance vs SPY) for
 * each sector ETF.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteSectorRsStreakRepository implements SectorRsStreakRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(SectorRsStreak streak) {
        String sql =
                """
                INSERT OR REPLACE INTO sector_rs_streaks
                (symbol, streak_days, is_outperforming, last_updated)
                VALUES (?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                streak.symbol(),
                streak.streakDays(),
                streak.isOutperforming() ? 1 : 0,
                streak.lastUpdated().toString());
        log.debug("Saved RS streak for {}: {} days", streak.symbol(), streak.streakDays());
    }

    @Override
    public Optional<SectorRsStreak> findBySymbol(String symbol) {
        String sql =
                """
                SELECT symbol, streak_days, is_outperforming, last_updated
                FROM sector_rs_streaks
                WHERE symbol = ?
                """;

        List<SectorRsStreak> results = jdbcTemplate.query(sql, this::mapRow, symbol);
        return results.stream().findFirst();
    }

    @Override
    public Map<String, SectorRsStreak> findAll() {
        String sql =
                """
                SELECT symbol, streak_days, is_outperforming, last_updated
                FROM sector_rs_streaks
                """;

        List<SectorRsStreak> results = jdbcTemplate.query(sql, this::mapRow);
        Map<String, SectorRsStreak> map = new HashMap<>();
        for (SectorRsStreak streak : results) {
            map.put(streak.symbol(), streak);
        }
        return map;
    }

    private SectorRsStreak mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SectorRsStreak(
                rs.getString("symbol"),
                rs.getInt("streak_days"),
                rs.getInt("is_outperforming") == 1,
                LocalDate.parse(rs.getString("last_updated")));
    }
}
