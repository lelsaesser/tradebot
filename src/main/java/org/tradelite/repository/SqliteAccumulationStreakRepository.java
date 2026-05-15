package org.tradelite.repository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tradelite.core.AccumulationStreak;

/**
 * SQLite implementation of {@link AccumulationStreakRepository}.
 *
 * <p>Stores accumulation streak data (consecutive days of institutional accumulation signal) for
 * each stock.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteAccumulationStreakRepository implements AccumulationStreakRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(AccumulationStreak streak) {
        String sql =
                """
                INSERT OR REPLACE INTO accumulation_streaks
                (symbol, streak_days, last_updated)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(
                sql, streak.symbol(), streak.streakDays(), streak.lastUpdated().toString());
        log.debug(
                "Saved accumulation streak for {}: {} days", streak.symbol(), streak.streakDays());
    }

    @Override
    public Optional<AccumulationStreak> findBySymbol(String symbol) {
        String sql =
                """
                SELECT symbol, streak_days, last_updated
                FROM accumulation_streaks
                WHERE symbol = ?
                """;

        List<AccumulationStreak> results = jdbcTemplate.query(sql, this::mapRow, symbol);
        return results.stream().findFirst();
    }

    @Override
    public void deleteAllExcept(Set<String> symbols) {
        if (symbols.isEmpty()) {
            jdbcTemplate.update("DELETE FROM accumulation_streaks");
            log.debug("Deleted all accumulation streaks");
            return;
        }

        String placeholders = String.join(",", Collections.nCopies(symbols.size(), "?"));
        String sql = "DELETE FROM accumulation_streaks WHERE symbol NOT IN (" + placeholders + ")";
        jdbcTemplate.update(sql, symbols.toArray());
        log.debug("Cleaned up accumulation streaks, keeping {} symbols", symbols.size());
    }

    private AccumulationStreak mapRow(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new AccumulationStreak(
                rs.getString("symbol"),
                rs.getInt("streak_days"),
                LocalDate.parse(rs.getString("last_updated")));
    }
}
