package org.tradelite.repository;

import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tradelite.common.SymbolLifecycleListener;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteNewlyAddedSymbolRepository
        implements NewlyAddedSymbolRepository, SymbolLifecycleListener {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void insert(String ticker, long addedAt) {
        String sql = "INSERT OR REPLACE INTO newly_added_symbols (ticker, added_at) VALUES (?, ?)";
        jdbcTemplate.update(sql, ticker, addedAt);
    }

    @Override
    public List<NewlyAddedSymbol> findOldest(int limit) {
        String sql =
                "SELECT ticker, added_at FROM newly_added_symbols ORDER BY added_at ASC LIMIT ?";
        return jdbcTemplate.query(
                sql,
                (rs, _) -> new NewlyAddedSymbol(rs.getString("ticker"), rs.getLong("added_at")),
                limit);
    }

    @Override
    public void deleteAll(List<String> tickers) {
        if (tickers.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(tickers.size(), "?"));
        String sql = "DELETE FROM newly_added_symbols WHERE ticker IN (" + placeholders + ")";
        jdbcTemplate.update(sql, tickers.toArray());
    }

    @Override
    public List<String> deleteExpiredReturning(long cutoffTimestamp) {
        String sql = "DELETE FROM newly_added_symbols WHERE added_at < ? RETURNING ticker";
        return jdbcTemplate.queryForList(sql, String.class, cutoffTimestamp);
    }

    @Override
    public int deleteByTicker(String ticker) {
        String sql = "DELETE FROM newly_added_symbols WHERE ticker = ?";
        int deleted = jdbcTemplate.update(sql, ticker);
        if (deleted > 0) {
            log.info("Deleted {} newly added symbol rows for ticker {}", deleted, ticker);
        }
        return deleted;
    }

    @Override
    public void onSymbolRemoved(String ticker) {
        deleteByTicker(ticker);
    }
}
