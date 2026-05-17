package org.tradelite.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tradelite.common.AssetType;
import org.tradelite.common.SymbolRegistry.StockSymbolEntry;

@Repository
@RequiredArgsConstructor
public class SqliteTrackedSymbolRepository implements TrackedSymbolRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<StockSymbolEntry> findAll() {
        String sql = "SELECT ticker, display_name FROM tracked_symbols ORDER BY ticker";
        return jdbcTemplate.query(
                sql,
                (rs, _) ->
                        new StockSymbolEntry(rs.getString("ticker"), rs.getString("display_name")));
    }

    @Override
    public List<StockSymbolEntry> findByAssetType(AssetType type) {
        String sql =
                "SELECT ticker, display_name FROM tracked_symbols WHERE asset_type = ? ORDER BY ticker";
        return jdbcTemplate.query(
                sql,
                (rs, _) ->
                        new StockSymbolEntry(rs.getString("ticker"), rs.getString("display_name")),
                type.name());
    }

    @Override
    public void save(String ticker, String displayName, AssetType type) {
        String sql =
                """
                INSERT OR REPLACE INTO tracked_symbols (ticker, display_name, asset_type)
                VALUES (?, ?, ?)
                """;
        jdbcTemplate.update(sql, ticker, displayName, type.name());
    }

    @Override
    public boolean deleteByTickerAndType(String ticker, AssetType type) {
        String sql = "DELETE FROM tracked_symbols WHERE ticker = ? AND asset_type = ?";
        return jdbcTemplate.update(sql, ticker, type.name()) > 0;
    }

    @Override
    public int count() {
        Integer result =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tracked_symbols", Integer.class);
        return result != null ? result : 0;
    }
}
