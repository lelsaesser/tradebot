package org.tradelite.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQLite implementation of {@link TreasuryIndicatorStateRepository}. Mirrors the shape of {@link
 * SqliteMomentumRocRepository}: INSERT OR REPLACE on save, single-row lookup by primary key.
 *
 * <p>The {@code last_observation_date} column is stored as ISO-8601 text (e.g. {@code
 * "2026-06-23"}) — SQLite has no native date type, and matching the existing {@code
 * sector_rs_streaks.last_updated} convention keeps the schema readable.
 *
 * <p>The {@code updated_at} column stores Unix epoch seconds (matching {@code
 * momentum_roc_state.updated_at}) for forensic comparison with wall-clock logs.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteTreasuryIndicatorStateRepository implements TreasuryIndicatorStateRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(TreasuryIndicatorState state) {
        String sql =
                """
                INSERT OR REPLACE INTO treasury_indicator_state
                (series_id, last_band, last_value, last_observation_date, initialized, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                state.seriesId(),
                state.lastBand(),
                state.lastValue(),
                state.lastObservationDate().toString(),
                state.initialized() ? 1 : 0,
                state.updatedAt().getEpochSecond());
        log.debug("Saved treasury indicator state for {}", state.seriesId());
    }

    @Override
    public Optional<TreasuryIndicatorState> findBySeriesId(String seriesId) {
        String sql =
                """
                SELECT series_id, last_band, last_value, last_observation_date, initialized,
                       updated_at
                FROM treasury_indicator_state
                WHERE series_id = ?
                """;

        List<TreasuryIndicatorState> results =
                jdbcTemplate.query(
                        sql,
                        (rs, _) ->
                                new TreasuryIndicatorState(
                                        rs.getString("series_id"),
                                        rs.getString("last_band"),
                                        rs.getDouble("last_value"),
                                        LocalDate.parse(rs.getString("last_observation_date")),
                                        rs.getInt("initialized") == 1,
                                        Instant.ofEpochSecond(rs.getLong("updated_at"))),
                        seriesId);
        return results.stream().findFirst();
    }
}
