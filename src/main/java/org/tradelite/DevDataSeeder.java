package org.tradelite;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradelite.common.SectorEtfRegistry;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.MomentumRocRepository;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RsiService;
import org.tradelite.service.StockSymbolRegistry;
import org.tradelite.service.model.DailyPrice;
import org.tradelite.service.model.MomentumRocData;
import org.tradelite.service.model.RelativeStrengthData;
import org.tradelite.service.model.RsiDailyClosePrice;

@Slf4j
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final int LOOKBACK_DAYS = 90;
    private static final int SAMPLE_STOCK_LIMIT = 5;
    private static final String QUOTES_TABLE = "finnhub_price_quotes";
    private static final String MOMENTUM_TABLE = "momentum_roc_state";
    private static final LocalTime SEEDED_CLOSE_TIME = LocalTime.of(20, 0);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final MomentumRocRepository momentumRocRepository;
    private final RsiService rsiService;
    private final RelativeStrengthService relativeStrengthService;
    private final TargetPriceProvider targetPriceProvider;
    private final StockSymbolRegistry stockSymbolRegistry;
    private final Path rsiDataFilePath;
    private final Path rsDataFilePath;

    public DevDataSeeder(
            DataSource dataSource,
            ObjectMapper objectMapper,
            MomentumRocRepository momentumRocRepository,
            RsiService rsiService,
            RelativeStrengthService relativeStrengthService,
            TargetPriceProvider targetPriceProvider,
            StockSymbolRegistry stockSymbolRegistry) {
        this(
                dataSource,
                objectMapper,
                momentumRocRepository,
                rsiService,
                relativeStrengthService,
                targetPriceProvider,
                stockSymbolRegistry,
                Path.of("config/rsi-data.json"),
                Path.of("config/rs-data.json"));
    }

    DevDataSeeder(
            DataSource dataSource,
            ObjectMapper objectMapper,
            MomentumRocRepository momentumRocRepository,
            RsiService rsiService,
            RelativeStrengthService relativeStrengthService,
            TargetPriceProvider targetPriceProvider,
            StockSymbolRegistry stockSymbolRegistry,
            Path rsiDataFilePath,
            Path rsDataFilePath) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.momentumRocRepository = momentumRocRepository;
        this.rsiService = rsiService;
        this.relativeStrengthService = relativeStrengthService;
        this.targetPriceProvider = targetPriceProvider;
        this.stockSymbolRegistry = stockSymbolRegistry;
        this.rsiDataFilePath = rsiDataFilePath;
        this.rsDataFilePath = rsDataFilePath;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfMissing();
    }

    public boolean seedIfMissing() {
        if (hasSeedData()) {
            log.info("Dev analytics seed data already present, skipping startup seed");
            return false;
        }

        reseed();
        return true;
    }

    public void reseed() {
        log.info("Seeding dev analytics data");
        SeedBundle bundle = buildSeedBundle();
        try {
            initializeSchema();
            clearExistingData();
            insertPriceHistory(bundle.priceSeriesBySymbol());
            seedRsiHistory(bundle);
            seedRelativeStrengthHistory(bundle);
            seedMomentumState(bundle);
            log.info(
                    "Seeded dev analytics data for {} symbols",
                    bundle.priceSeriesBySymbol().size());
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to seed dev analytics data", e);
        }
    }

    private boolean hasSeedData() {
        return Files.exists(rsiDataFilePath)
                && Files.exists(rsDataFilePath)
                && hasPersistedQuotes(SectorEtfRegistry.BENCHMARK_SYMBOL);
    }

    private boolean hasPersistedQuotes(String symbol) {
        String sql =
                "SELECT COUNT(*) FROM " + QUOTES_TABLE + " WHERE symbol = ? AND current_price > 0";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void initializeSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS finnhub_price_quotes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        symbol TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        current_price REAL NOT NULL,
                        daily_open REAL,
                        daily_high REAL,
                        daily_low REAL,
                        change_amount REAL,
                        change_percent REAL,
                        previous_close REAL,
                        UNIQUE(symbol, timestamp)
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS momentum_roc_state (
                        symbol TEXT PRIMARY KEY,
                        previous_roc10 REAL NOT NULL,
                        previous_roc20 REAL NOT NULL,
                        initialized INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    private void clearExistingData() throws SQLException, IOException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + QUOTES_TABLE);
            statement.executeUpdate("DELETE FROM " + MOMENTUM_TABLE);
        }

        Files.deleteIfExists(rsiDataFilePath);
        Files.deleteIfExists(rsDataFilePath);
        rsiService.getPriceHistory().clear();
        rsiService.getSymbolDisplayNames().clear();
        relativeStrengthService.getRsHistory().clear();
    }

    private SeedBundle buildSeedBundle() {
        Map<String, String> displayNames =
                new LinkedHashMap<>(SectorEtfRegistry.allEtfsWithBenchmark());

        int stockCount = 0;
        for (TargetPrice targetPrice : targetPriceProvider.getStockTargetPrices()) {
            if (stockCount >= SAMPLE_STOCK_LIMIT) {
                break;
            }

            stockSymbolRegistry
                    .fromString(targetPrice.getSymbol())
                    .filter(symbol -> !stockSymbolRegistry.isEtf(symbol.getTicker()))
                    .ifPresent(
                            stockSymbol -> {
                                if (!displayNames.containsKey(stockSymbol.getTicker())) {
                                    displayNames.put(
                                            stockSymbol.getTicker(), stockSymbol.getCompanyName());
                                }
                            });
            if (displayNames.containsKey(targetPrice.getSymbol())) {
                stockCount++;
            }
        }

        if (stockCount == 0) {
            for (StockSymbol stockSymbol : stockSymbolRegistry.getAll()) {
                if (stockCount >= SAMPLE_STOCK_LIMIT) {
                    break;
                }
                if (stockSymbolRegistry.isEtf(stockSymbol.getTicker())) {
                    continue;
                }
                displayNames.putIfAbsent(stockSymbol.getTicker(), stockSymbol.getCompanyName());
                stockCount++;
            }
        }

        Map<String, List<DailyPrice>> priceSeriesBySymbol = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : displayNames.entrySet()) {
            priceSeriesBySymbol.put(entry.getKey(), buildPriceSeries(entry.getKey()));
        }

        return new SeedBundle(displayNames, priceSeriesBySymbol);
    }

    private List<DailyPrice> buildPriceSeries(String symbol) {
        int hash = Math.abs(symbol.hashCode());
        double basePrice = 80.0 + (hash % 120);
        double dailySlope = ((hash % 11) - 5) * 0.18;
        double amplitude = 3.0 + (hash % 7);
        double phase = (hash % 13) / 3.0;

        return java.util.stream.IntStream.range(0, LOOKBACK_DAYS)
                .mapToObj(
                        index -> {
                            LocalDate date = LocalDate.now().minusDays(LOOKBACK_DAYS - 1L - index);
                            double seasonal =
                                    Math.sin((index + phase) / 5.0) * amplitude
                                            + Math.cos((index + phase) / 11.0) * (amplitude / 2.0);
                            double price =
                                    roundTo2Decimals(
                                            Math.max(
                                                    15.0,
                                                    basePrice + (index * dailySlope) + seasonal));
                            DailyPrice dailyPrice = new DailyPrice();
                            dailyPrice.setDate(date);
                            dailyPrice.setPrice(price);
                            return dailyPrice;
                        })
                .toList();
    }

    private void insertPriceHistory(Map<String, List<DailyPrice>> priceSeriesBySymbol)
            throws SQLException {
        String sql =
                """
                INSERT OR REPLACE INTO finnhub_price_quotes
                (symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                 change_amount, change_percent, previous_close)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, List<DailyPrice>> entry : priceSeriesBySymbol.entrySet()) {
                List<DailyPrice> series = entry.getValue();
                for (int index = 0; index < series.size(); index++) {
                    DailyPrice current = series.get(index);
                    double previousClose =
                            index == 0
                                    ? roundTo2Decimals(current.getPrice() * 0.99)
                                    : series.get(index - 1).getPrice();
                    double open = roundTo2Decimals((current.getPrice() + previousClose) / 2.0);
                    double high = roundTo2Decimals(Math.max(open, current.getPrice()) * 1.01);
                    double low = roundTo2Decimals(Math.min(open, current.getPrice()) * 0.99);
                    double change = roundTo2Decimals(current.getPrice() - previousClose);
                    double changePercent =
                            previousClose == 0.0
                                    ? 0.0
                                    : roundTo2Decimals((change / previousClose) * 100.0);
                    long timestamp =
                            current.getDate()
                                    .atTime(SEEDED_CLOSE_TIME)
                                    .toEpochSecond(ZoneOffset.UTC);

                    statement.setString(1, entry.getKey());
                    statement.setLong(2, timestamp);
                    statement.setDouble(3, current.getPrice());
                    statement.setDouble(4, open);
                    statement.setDouble(5, high);
                    statement.setDouble(6, low);
                    statement.setDouble(7, change);
                    statement.setDouble(8, changePercent);
                    statement.setDouble(9, previousClose);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void seedRsiHistory(SeedBundle bundle) throws IOException {
        Map<String, RsiDailyClosePrice> history = rsiService.getPriceHistory();
        Map<String, String> displayNames = rsiService.getSymbolDisplayNames();

        history.clear();
        displayNames.clear();

        for (Map.Entry<String, List<DailyPrice>> entry : bundle.priceSeriesBySymbol().entrySet()) {
            RsiDailyClosePrice closePrice = new RsiDailyClosePrice();
            for (DailyPrice dailyPrice : entry.getValue()) {
                closePrice.addPrice(dailyPrice.getDate(), dailyPrice.getPrice());
            }
            history.put(entry.getKey(), closePrice);
            displayNames.put(
                    entry.getKey(),
                    bundle.displayNamesBySymbol().getOrDefault(entry.getKey(), entry.getKey()));
        }

        ensureParentDirectories(rsiDataFilePath);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(rsiDataFilePath.toFile(), history);
    }

    private void seedRelativeStrengthHistory(SeedBundle bundle) throws IOException {
        Map<String, RelativeStrengthData> rsHistory = relativeStrengthService.getRsHistory();
        rsHistory.clear();

        List<DailyPrice> spySeries =
                bundle.priceSeriesBySymbol().get(SectorEtfRegistry.BENCHMARK_SYMBOL);
        Map<LocalDate, Double> spyByDate = new LinkedHashMap<>();
        for (DailyPrice dailyPrice : spySeries) {
            spyByDate.put(dailyPrice.getDate(), dailyPrice.getPrice());
        }

        for (Map.Entry<String, List<DailyPrice>> entry : bundle.priceSeriesBySymbol().entrySet()) {
            if (SectorEtfRegistry.BENCHMARK_SYMBOL.equals(entry.getKey())) {
                continue;
            }

            RelativeStrengthData rsData = new RelativeStrengthData();
            for (DailyPrice dailyPrice : entry.getValue()) {
                Double spyPrice = spyByDate.get(dailyPrice.getDate());
                if (spyPrice == null || spyPrice <= 0.0) {
                    continue;
                }
                rsData.addRsValue(dailyPrice.getDate(), dailyPrice.getPrice() / spyPrice);
            }

            List<Double> rsValues = rsData.getRsValues();
            if (rsValues.isEmpty()) {
                continue;
            }

            int emaPeriod = Math.min(50, rsValues.size());
            rsData.setPreviousRs(rsValues.get(rsValues.size() - 1));
            rsData.setPreviousEma(calculateEma(rsValues, emaPeriod));
            rsData.setInitialized(true);
            rsHistory.put(entry.getKey(), rsData);
        }

        ensureParentDirectories(rsDataFilePath);
        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(rsDataFilePath.toFile(), rsHistory);
    }

    private void seedMomentumState(SeedBundle bundle) {
        for (String symbol : SectorEtfRegistry.allEtfs().keySet()) {
            List<DailyPrice> series = bundle.priceSeriesBySymbol().get(symbol);
            if (series == null || series.size() <= 20) {
                continue;
            }

            MomentumRocData momentumRocData = new MomentumRocData();
            momentumRocData.setPreviousRoc10(calculateRoc(series, 10));
            momentumRocData.setPreviousRoc20(calculateRoc(series, 20));
            momentumRocData.setInitialized(true);
            momentumRocRepository.save(symbol, momentumRocData);
        }
    }

    private double calculateRoc(List<DailyPrice> series, int period) {
        if (series.size() <= period) {
            return 0.0;
        }

        double currentPrice = series.get(series.size() - 1).getPrice();
        double pastPrice = series.get(series.size() - 1 - period).getPrice();
        if (pastPrice == 0.0) {
            return 0.0;
        }
        return ((currentPrice - pastPrice) / pastPrice) * 100.0;
    }

    private double calculateEma(List<Double> values, int period) {
        if (values.isEmpty() || period <= 0) {
            return 0.0;
        }

        double multiplier = 2.0 / (period + 1);
        double ema =
                values.subList(0, period).stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
        for (int index = period; index < values.size(); index++) {
            ema = (values.get(index) - ema) * multiplier + ema;
        }
        return ema;
    }

    private void ensureParentDirectories(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private double roundTo2Decimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    record SeedBundle(
            Map<String, String> displayNamesBySymbol,
            Map<String, List<DailyPrice>> priceSeriesBySymbol) {}
}
