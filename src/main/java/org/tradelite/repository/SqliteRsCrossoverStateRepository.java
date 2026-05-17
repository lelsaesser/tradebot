package org.tradelite.repository;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tradelite.service.model.RelativeStrengthData;

/**
 * SQLite implementation of {@link RsCrossoverStateRepository}.
 *
 * <p>Stores RS crossover state (previous RS/EMA values and initialized flag) for each symbol to
 * enable crossover detection between calculation cycles.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteRsCrossoverStateRepository implements RsCrossoverStateRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(String symbol, RelativeStrengthData data) {
        String sql =
                """
                INSERT OR REPLACE INTO rs_crossover_state
                (symbol, previous_rs, previous_ema, initialized, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        long timestamp = System.currentTimeMillis() / 1000;
        jdbcTemplate.update(
                sql,
                symbol,
                data.getPreviousRs(),
                data.getPreviousEma(),
                data.isInitialized() ? 1 : 0,
                timestamp);
        log.debug("Saved RS crossover state for {}", symbol);
    }

    @Override
    public Map<String, RelativeStrengthData> findAll() {
        String sql =
                """
                SELECT symbol, previous_rs, previous_ema, initialized
                FROM rs_crossover_state
                """;

        Map<String, RelativeStrengthData> result = new HashMap<>();
        jdbcTemplate.query(
                sql,
                (rs, _) -> {
                    String symbol = rs.getString("symbol");
                    RelativeStrengthData data = new RelativeStrengthData();
                    data.setPreviousRs(rs.getDouble("previous_rs"));
                    data.setPreviousEma(rs.getDouble("previous_ema"));
                    data.setInitialized(rs.getInt("initialized") == 1);
                    result.put(symbol, data);
                    return data;
                });
        return result;
    }
}
