package org.tradelite.repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.tradelite.common.SymbolLifecycleListener;

/**
 * SQLite implementation of {@link ApexPerformerRepository}.
 *
 * <p>Stores the current apex performer set (stocks outperforming the top sector ETF). The set is
 * refreshed atomically on each daily summary run.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteApexPerformerRepository
        implements ApexPerformerRepository, SymbolLifecycleListener {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void replaceAll(Set<String> symbols) {
        jdbcTemplate.update("DELETE FROM apex_performers");

        if (symbols.isEmpty()) {
            log.debug("Replaced apex_performers with empty set");
            return;
        }

        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        List<Object[]> batch = symbols.stream().map(s -> new Object[] {s, today}).toList();
        jdbcTemplate.batchUpdate(
                "INSERT INTO apex_performers (symbol, last_updated) VALUES (?, ?)", batch);
        log.debug("Replaced apex_performers with {} symbols", symbols.size());
    }

    @Override
    public Set<String> findAll() {
        List<String> rows =
                jdbcTemplate.queryForList("SELECT symbol FROM apex_performers", String.class);
        return new HashSet<>(rows);
    }

    @Override
    public int deleteBySymbol(String symbol) {
        String sql = "DELETE FROM apex_performers WHERE symbol = ?";
        int deleted = jdbcTemplate.update(sql, symbol);
        if (deleted > 0) {
            log.info("Deleted {} apex performer rows for symbol {}", deleted, symbol);
        }
        return deleted;
    }

    @Override
    public void onSymbolRemoved(String ticker) {
        deleteBySymbol(ticker);
    }
}
