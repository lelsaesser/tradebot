package org.tradelite.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.service.model.DailyPrice;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SqlitePriceQuoteRepository implements PriceQuoteRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(PriceQuoteResponse priceQuote) {
        String sql =
                """
                INSERT OR REPLACE INTO finnhub_price_quotes
                (symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                 change_amount, change_percent, previous_close)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        long timestamp =
                priceQuote.getTimestamp() > 0
                        ? priceQuote.getTimestamp()
                        : Instant.now().getEpochSecond();
        jdbcTemplate.update(
                sql,
                priceQuote.getStockSymbol().getTicker(),
                timestamp,
                priceQuote.getCurrentPrice(),
                priceQuote.getDailyOpen(),
                priceQuote.getDailyHigh(),
                priceQuote.getDailyLow(),
                priceQuote.getChange(),
                priceQuote.getChangePercent(),
                priceQuote.getPreviousClose());
        log.debug(
                "Saved price quote for {} at timestamp {}",
                priceQuote.getStockSymbol().getTicker(),
                timestamp);
    }

    @Override
    public void saveAll(List<PriceQuoteResponse> priceQuotes) {
        if (priceQuotes.isEmpty()) {
            return;
        }

        String sql =
                """
                INSERT OR REPLACE INTO finnhub_price_quotes
                (symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                 change_amount, change_percent, previous_close)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(
                sql,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        PriceQuoteResponse pq = priceQuotes.get(i);
                        ps.setString(1, pq.getStockSymbol().getTicker());
                        ps.setLong(2, pq.getTimestamp());
                        ps.setDouble(3, pq.getCurrentPrice());
                        ps.setDouble(4, pq.getDailyOpen());
                        ps.setDouble(5, pq.getDailyHigh());
                        ps.setDouble(6, pq.getDailyLow());
                        ps.setDouble(7, pq.getChange());
                        ps.setDouble(8, pq.getChangePercent());
                        ps.setDouble(9, pq.getPreviousClose());
                    }

                    @Override
                    public int getBatchSize() {
                        return priceQuotes.size();
                    }
                });
        log.debug("Saved {} price quotes in batch", priceQuotes.size());
    }

    @Override
    public Optional<PriceQuoteEntity> findLatestBySymbol(String symbol) {
        String sql =
                """
                SELECT id, symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                       change_amount, change_percent, previous_close
                FROM finnhub_price_quotes
                WHERE symbol = ?
                ORDER BY timestamp DESC
                LIMIT 1
                """;

        List<PriceQuoteEntity> results =
                jdbcTemplate.query(sql, this::mapResultSetToEntity, symbol);
        return results.stream().findFirst();
    }

    @Override
    public List<PriceQuoteEntity> findBySymbol(String symbol) {
        String sql =
                """
                SELECT id, symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                       change_amount, change_percent, previous_close
                FROM finnhub_price_quotes
                WHERE symbol = ?
                ORDER BY timestamp ASC
                """;

        return jdbcTemplate.query(sql, this::mapResultSetToEntity, symbol);
    }

    @Override
    public List<PriceQuoteEntity> findBySymbolAndDate(String symbol, LocalDate date) {
        long startOfDay = date.atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
        long endOfDay = date.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toEpochSecond();

        return findBySymbolAndTimestampRange(symbol, startOfDay, endOfDay);
    }

    @Override
    public List<PriceQuoteEntity> findBySymbolAndDateRange(
            String symbol, LocalDate startDate, LocalDate endDate) {
        long startTimestamp = startDate.atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
        long endTimestamp = endDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toEpochSecond();

        return findBySymbolAndTimestampRange(symbol, startTimestamp, endTimestamp);
    }

    private List<PriceQuoteEntity> findBySymbolAndTimestampRange(
            String symbol, long startTimestamp, long endTimestamp) {
        String sql =
                """
                SELECT id, symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                       change_amount, change_percent, previous_close
                FROM finnhub_price_quotes
                WHERE symbol = ? AND timestamp >= ? AND timestamp < ?
                ORDER BY timestamp ASC
                """;

        return jdbcTemplate.query(
                sql, this::mapResultSetToEntity, symbol, startTimestamp, endTimestamp);
    }

    private PriceQuoteEntity mapResultSetToEntity(ResultSet rs, int rowNum) throws SQLException {
        return PriceQuoteEntity.builder()
                .id(rs.getLong("id"))
                .symbol(rs.getString("symbol"))
                .timestamp(rs.getLong("timestamp"))
                .currentPrice(rs.getDouble("current_price"))
                .dailyOpen(rs.getDouble("daily_open"))
                .dailyHigh(rs.getDouble("daily_high"))
                .dailyLow(rs.getDouble("daily_low"))
                .changeAmount(rs.getDouble("change_amount"))
                .changePercent(rs.getDouble("change_percent"))
                .previousClose(rs.getDouble("previous_close"))
                .build();
    }

    @Override
    public List<DailyPrice> findDailyClosingPrices(String symbol, int days) {
        long startTimestamp =
                LocalDate.now().minusDays(days).atStartOfDay(ZoneId.of("UTC")).toEpochSecond();

        String sql =
                """
                SELECT date(timestamp, 'unixepoch', 'localtime') as price_date,
                       current_price
                FROM finnhub_price_quotes
                WHERE symbol = ? AND timestamp >= ?
                GROUP BY price_date
                HAVING timestamp = MAX(timestamp)
                ORDER BY price_date ASC
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    DailyPrice dailyPrice = new DailyPrice();
                    dailyPrice.setDate(LocalDate.parse(rs.getString("price_date")));
                    dailyPrice.setPrice(rs.getDouble("current_price"));
                    return dailyPrice;
                },
                symbol,
                startTimestamp);
    }

    @Override
    public List<Double> findDailyChangePercents(String symbol, int days) {
        long startTimestamp =
                LocalDate.now().minusDays(days).atStartOfDay(ZoneId.of("UTC")).toEpochSecond();

        String sql =
                """
                SELECT date(timestamp, 'unixepoch', 'localtime') as price_date,
                       change_percent
                FROM finnhub_price_quotes
                WHERE symbol = ? AND timestamp >= ?
                GROUP BY price_date
                HAVING timestamp = MAX(timestamp)
                ORDER BY price_date ASC
                """;

        return jdbcTemplate.query(
                sql, (rs, rowNum) -> rs.getDouble("change_percent"), symbol, startTimestamp);
    }
}
