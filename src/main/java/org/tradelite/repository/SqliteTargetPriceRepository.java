package org.tradelite.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tradelite.common.AssetType;
import org.tradelite.common.TargetPrice;

@Repository
@RequiredArgsConstructor
public class SqliteTargetPriceRepository implements TargetPriceRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<TargetPrice> findByAssetType(AssetType type) {
        String sql =
                "SELECT symbol, buy_target, sell_target FROM target_prices WHERE asset_type = ?";
        return jdbcTemplate.query(
                sql,
                (rs, _) ->
                        new TargetPrice(
                                rs.getString("symbol"),
                                rs.getDouble("buy_target"),
                                rs.getDouble("sell_target")),
                type.name());
    }

    @Override
    public void save(TargetPrice targetPrice, AssetType type) {
        String sql =
                """
                INSERT OR REPLACE INTO target_prices (symbol, asset_type, buy_target, sell_target)
                VALUES (?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                targetPrice.getSymbol(),
                type.name(),
                targetPrice.getBuyTarget(),
                targetPrice.getSellTarget());
    }

    @Override
    public boolean deleteBySymbolAndType(String symbol, AssetType type) {
        String sql = "DELETE FROM target_prices WHERE symbol = ? AND asset_type = ?";
        return jdbcTemplate.update(sql, symbol, type.name()) > 0;
    }

    @Override
    public int count() {
        Integer result =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM target_prices", Integer.class);
        return result != null ? result : 0;
    }
}
