package org.tradelite.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tradelite.common.SymbolRegistry.StockSymbolEntry;

@Repository
@RequiredArgsConstructor
public class SqliteStockSymbolRepository implements StockSymbolRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<StockSymbolEntry> findAll() {
        String sql = "SELECT ticker, display_name FROM stock_symbols ORDER BY ticker";
        return jdbcTemplate.query(
                sql,
                (rs, _) ->
                        new StockSymbolEntry(rs.getString("ticker"), rs.getString("display_name")));
    }

    @Override
    public void save(String ticker, String displayName) {
        String sql =
                """
                INSERT OR REPLACE INTO stock_symbols (ticker, display_name)
                VALUES (?, ?)
                """;
        jdbcTemplate.update(sql, ticker, displayName);
    }

    @Override
    public boolean deleteByTicker(String ticker) {
        String sql = "DELETE FROM stock_symbols WHERE ticker = ?";
        return jdbcTemplate.update(sql, ticker) > 0;
    }

    @Override
    public int count() {
        Integer result =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stock_symbols", Integer.class);
        return result != null ? result : 0;
    }
}
