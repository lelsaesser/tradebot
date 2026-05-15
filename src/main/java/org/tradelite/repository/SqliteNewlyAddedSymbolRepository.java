package org.tradelite.repository;

import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SqliteNewlyAddedSymbolRepository implements NewlyAddedSymbolRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void insert(String ticker, long addedAt) {
        String sql = "INSERT OR IGNORE INTO newly_added_symbols (ticker, added_at) VALUES (?, ?)";
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
    public List<String> findExpired(long cutoffTimestamp) {
        String sql = "SELECT ticker FROM newly_added_symbols WHERE added_at < ?";
        return jdbcTemplate.queryForList(sql, String.class, cutoffTimestamp);
    }

    @Override
    public void deleteExpired(long cutoffTimestamp) {
        String sql = "DELETE FROM newly_added_symbols WHERE added_at < ?";
        jdbcTemplate.update(sql, cutoffTimestamp);
    }
}
