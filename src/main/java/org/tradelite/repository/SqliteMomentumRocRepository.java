package org.tradelite.repository;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tradelite.service.model.MomentumRocData;

/**
 * SQLite implementation of {@link MomentumRocRepository}.
 *
 * <p>Stores momentum ROC state data (previous ROC values) for each symbol to enable crossover
 * detection between daily calculation cycles.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteMomentumRocRepository implements MomentumRocRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(String symbol, MomentumRocData data) {
        String sql =
                """
                INSERT OR REPLACE INTO momentum_roc_state
                (symbol, previous_roc10, previous_roc20, initialized, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        long timestamp = System.currentTimeMillis() / 1000;
        jdbcTemplate.update(
                sql,
                symbol,
                data.getPreviousRoc10(),
                data.getPreviousRoc20(),
                data.isInitialized() ? 1 : 0,
                timestamp);
        log.debug("Saved momentum ROC state for {}", symbol);
    }

    @Override
    public Optional<MomentumRocData> findBySymbol(String symbol) {
        String sql =
                """
                SELECT previous_roc10, previous_roc20, initialized
                FROM momentum_roc_state
                WHERE symbol = ?
                """;

        List<MomentumRocData> results =
                jdbcTemplate.query(
                        sql,
                        (rs, _) -> {
                            MomentumRocData data = new MomentumRocData();
                            data.setPreviousRoc10(rs.getDouble("previous_roc10"));
                            data.setPreviousRoc20(rs.getDouble("previous_roc20"));
                            data.setInitialized(rs.getInt("initialized") == 1);
                            return data;
                        },
                        symbol);
        return results.stream().findFirst();
    }
}
