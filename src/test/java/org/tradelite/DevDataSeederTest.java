package org.tradelite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.sqlite.SQLiteDataSource;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.MomentumRocRepository;
import org.tradelite.repository.OhlcvRepository;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RsiService;
import org.tradelite.service.StockSymbolRegistry;
import org.tradelite.service.model.RelativeStrengthData;
import org.tradelite.service.model.RsiDailyClosePrice;

class DevDataSeederTest {

    @TempDir Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpObjectMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void reseed_populatesSqliteAndIndicatorState() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        Path dbPath = tempDir.resolve("tradebot-dev.db");
        dataSource.setUrl("jdbc:sqlite:" + dbPath);

        MomentumRocRepository momentumRocRepository = mock(MomentumRocRepository.class);
        RsiService rsiService = mock(RsiService.class);
        RelativeStrengthService relativeStrengthService = mock(RelativeStrengthService.class);
        TargetPriceProvider targetPriceProvider = mock(TargetPriceProvider.class);
        StockSymbolRegistry stockSymbolRegistry = mock(StockSymbolRegistry.class);
        OhlcvRepository ohlcvRepository = mock(OhlcvRepository.class);

        var rsiHistory = new java.util.HashMap<String, RsiDailyClosePrice>();
        var symbolDisplayNames = new java.util.HashMap<String, String>();
        var rsHistory = new java.util.HashMap<String, RelativeStrengthData>();

        when(rsiService.getPriceHistory()).thenReturn(rsiHistory);
        when(rsiService.getSymbolDisplayNames()).thenReturn(symbolDisplayNames);
        when(relativeStrengthService.getRsHistory()).thenReturn(rsHistory);
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(List.of(new TargetPrice("AAPL", 150.0, 200.0)));
        when(stockSymbolRegistry.fromString("AAPL"))
                .thenReturn(java.util.Optional.of(new StockSymbol("AAPL", "Apple Inc")));
        when(stockSymbolRegistry.isEtf("AAPL")).thenReturn(false);

        DevDataSeeder seeder =
                new DevDataSeeder(
                        dataSource,
                        objectMapper,
                        momentumRocRepository,
                        rsiService,
                        relativeStrengthService,
                        targetPriceProvider,
                        stockSymbolRegistry,
                        ohlcvRepository,
                        tempDir.resolve("rsi-data.json"),
                        tempDir.resolve("rs-data.json"));

        seeder.reseed();

        assertThat(Files.exists(tempDir.resolve("rsi-data.json")), is(true));
        assertThat(Files.exists(tempDir.resolve("rs-data.json")), is(true));
        assertThat(rsiHistory, hasKey("SPY"));
        assertThat(rsiHistory, hasKey("AAPL"));
        assertThat(symbolDisplayNames.get("AAPL"), is("Apple Inc"));
        assertThat(rsHistory, hasKey("AAPL"));

        verify(momentumRocRepository, atLeastOnce()).save(any(), any());
        verify(ohlcvRepository, atLeastOnce()).saveAll(anyList());

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM finnhub_price_quotes WHERE symbol = 'SPY'")) {
            resultSet.next();
            assertThat(resultSet.getInt(1), greaterThan(0));
        }
    }

    @Test
    void seedIfMissing_skipsWhenFilesAndBenchmarkDataAlreadyExist() throws Exception {
        DataSource dataSource = createPreseededDataSource();

        Files.writeString(tempDir.resolve("rsi-data.json"), "{}");
        Files.writeString(tempDir.resolve("rs-data.json"), "{}");

        MomentumRocRepository momentumRocRepository = mock(MomentumRocRepository.class);
        RsiService rsiService = mock(RsiService.class);
        RelativeStrengthService relativeStrengthService = mock(RelativeStrengthService.class);
        TargetPriceProvider targetPriceProvider = mock(TargetPriceProvider.class);
        StockSymbolRegistry stockSymbolRegistry = mock(StockSymbolRegistry.class);
        OhlcvRepository ohlcvRepository = mock(OhlcvRepository.class);

        when(rsiService.getPriceHistory()).thenReturn(new java.util.HashMap<>());
        when(rsiService.getSymbolDisplayNames()).thenReturn(new java.util.HashMap<>());
        when(relativeStrengthService.getRsHistory()).thenReturn(new java.util.HashMap<>());
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());
        when(stockSymbolRegistry.getAll()).thenReturn(List.of());

        DevDataSeeder seeder =
                new DevDataSeeder(
                        dataSource,
                        objectMapper,
                        momentumRocRepository,
                        rsiService,
                        relativeStrengthService,
                        targetPriceProvider,
                        stockSymbolRegistry,
                        ohlcvRepository,
                        tempDir.resolve("rsi-data.json"),
                        tempDir.resolve("rs-data.json"));

        assertThat(seeder.seedIfMissing(), is(false));
    }

    @Test
    void run_delegatesToSeedIfMissing() {
        DevDataSeeder seeder = spy(createSeederWithEmptySources());
        doReturn(false).when(seeder).seedIfMissing();

        seeder.run(new DefaultApplicationArguments(new String[0]));

        verify(seeder).seedIfMissing();
    }

    @Test
    void seedIfMissing_reseedsWhenFilesAreAbsent() {
        Path rsiPath = tempDir.resolve("seed-if-missing-rsi.json");
        Path rsPath = tempDir.resolve("seed-if-missing-rs.json");
        DevDataSeeder seeder = createSeederWithEmptySources(rsiPath, rsPath);

        boolean seeded = seeder.seedIfMissing();

        assertThat(seeded, is(true));
        assertThat(Files.exists(rsiPath), is(true));
        assertThat(Files.exists(rsPath), is(true));
    }

    @Test
    void reseed_usesStockRegistryFallbackAndLimitsToFiveNonEtfs() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        Path dbPath = tempDir.resolve("fallback.db");
        dataSource.setUrl("jdbc:sqlite:" + dbPath);

        MomentumRocRepository momentumRocRepository = mock(MomentumRocRepository.class);
        RsiService rsiService = mock(RsiService.class);
        RelativeStrengthService relativeStrengthService = mock(RelativeStrengthService.class);
        TargetPriceProvider targetPriceProvider = mock(TargetPriceProvider.class);
        StockSymbolRegistry stockSymbolRegistry = mock(StockSymbolRegistry.class);
        OhlcvRepository ohlcvRepository = mock(OhlcvRepository.class);

        var rsiHistory = new java.util.HashMap<String, RsiDailyClosePrice>();
        var symbolDisplayNames = new java.util.HashMap<String, String>();
        var rsHistory = new java.util.HashMap<String, RelativeStrengthData>();

        when(rsiService.getPriceHistory()).thenReturn(rsiHistory);
        when(rsiService.getSymbolDisplayNames()).thenReturn(symbolDisplayNames);
        when(relativeStrengthService.getRsHistory()).thenReturn(rsHistory);
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());
        when(stockSymbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("ETF1", "ETF One"),
                                new StockSymbol("AAPL", "Apple Inc"),
                                new StockSymbol("MSFT", "Microsoft Corp"),
                                new StockSymbol("NVDA", "NVIDIA Corp"),
                                new StockSymbol("AMZN", "Amazon.com"),
                                new StockSymbol("META", "Meta Platforms"),
                                new StockSymbol("GOOG", "Alphabet Inc")));
        when(stockSymbolRegistry.isEtf("ETF1")).thenReturn(true);
        when(stockSymbolRegistry.isEtf("AAPL")).thenReturn(false);
        when(stockSymbolRegistry.isEtf("MSFT")).thenReturn(false);
        when(stockSymbolRegistry.isEtf("NVDA")).thenReturn(false);
        when(stockSymbolRegistry.isEtf("AMZN")).thenReturn(false);
        when(stockSymbolRegistry.isEtf("META")).thenReturn(false);
        when(stockSymbolRegistry.isEtf("GOOG")).thenReturn(false);

        DevDataSeeder seeder =
                new DevDataSeeder(
                        dataSource,
                        objectMapper,
                        momentumRocRepository,
                        rsiService,
                        relativeStrengthService,
                        targetPriceProvider,
                        stockSymbolRegistry,
                        ohlcvRepository,
                        tempDir.resolve("rsi-fallback.json"),
                        tempDir.resolve("rs-fallback.json"));

        seeder.reseed();

        assertThat(symbolDisplayNames, hasKey("AAPL"));
        assertThat(symbolDisplayNames, hasKey("MSFT"));
        assertThat(symbolDisplayNames, hasKey("NVDA"));
        assertThat(symbolDisplayNames, hasKey("AMZN"));
        assertThat(symbolDisplayNames, hasKey("META"));
        assertThat(symbolDisplayNames.containsKey("GOOG"), is(false));
        assertThat(symbolDisplayNames.containsKey("ETF1"), is(false));
    }

    @Test
    void reseed_wrapsSqlExceptions() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("db down"));

        MomentumRocRepository momentumRocRepository = mock(MomentumRocRepository.class);
        RsiService rsiService = mock(RsiService.class);
        RelativeStrengthService relativeStrengthService = mock(RelativeStrengthService.class);
        TargetPriceProvider targetPriceProvider = mock(TargetPriceProvider.class);
        StockSymbolRegistry stockSymbolRegistry = mock(StockSymbolRegistry.class);
        OhlcvRepository ohlcvRepository = mock(OhlcvRepository.class);

        when(rsiService.getPriceHistory()).thenReturn(new java.util.HashMap<>());
        when(rsiService.getSymbolDisplayNames()).thenReturn(new java.util.HashMap<>());
        when(relativeStrengthService.getRsHistory()).thenReturn(new java.util.HashMap<>());
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());
        when(stockSymbolRegistry.getAll()).thenReturn(List.of());

        DevDataSeeder seeder =
                new DevDataSeeder(
                        dataSource,
                        objectMapper,
                        momentumRocRepository,
                        rsiService,
                        relativeStrengthService,
                        targetPriceProvider,
                        stockSymbolRegistry,
                        ohlcvRepository,
                        tempDir.resolve("rsi-error.json"),
                        tempDir.resolve("rs-error.json"));

        IllegalStateException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalStateException.class, seeder::reseed);

        assertThat(exception.getMessage(), is("Failed to seed dev analytics data"));
    }

    private DevDataSeeder createSeederWithEmptySources() {
        return createSeederWithEmptySources(
                tempDir.resolve("generic-rsi.json"), tempDir.resolve("generic-rs.json"));
    }

    private DevDataSeeder createSeederWithEmptySources(Path rsiPath, Path rsPath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        Path dbPath = tempDir.resolve("generic.db");
        dataSource.setUrl("jdbc:sqlite:" + dbPath);

        MomentumRocRepository momentumRocRepository = mock(MomentumRocRepository.class);
        RsiService rsiService = mock(RsiService.class);
        RelativeStrengthService relativeStrengthService = mock(RelativeStrengthService.class);
        TargetPriceProvider targetPriceProvider = mock(TargetPriceProvider.class);
        StockSymbolRegistry stockSymbolRegistry = mock(StockSymbolRegistry.class);
        OhlcvRepository ohlcvRepository = mock(OhlcvRepository.class);

        when(rsiService.getPriceHistory()).thenReturn(new java.util.HashMap<>());
        when(rsiService.getSymbolDisplayNames()).thenReturn(new java.util.HashMap<>());
        when(relativeStrengthService.getRsHistory()).thenReturn(new java.util.HashMap<>());
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());
        when(stockSymbolRegistry.getAll()).thenReturn(List.of());

        return new DevDataSeeder(
                dataSource,
                objectMapper,
                momentumRocRepository,
                rsiService,
                relativeStrengthService,
                targetPriceProvider,
                stockSymbolRegistry,
                ohlcvRepository,
                rsiPath,
                rsPath);
    }

    private DataSource createPreseededDataSource() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        Path dbPath = tempDir.resolve("preseeded.db");
        dataSource.setUrl("jdbc:sqlite:" + dbPath);

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
            statement.executeUpdate(
                    """
                    INSERT INTO finnhub_price_quotes
                    (symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                     change_amount, change_percent, previous_close)
                    VALUES ('SPY', 1, 500.0, 500.0, 501.0, 499.0, 1.0, 0.2, 499.0)
                    """);
        }

        return dataSource;
    }
}
