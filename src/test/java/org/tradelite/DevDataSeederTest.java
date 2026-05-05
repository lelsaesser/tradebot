package org.tradelite;

import static org.hamcrest.MatcherAssert.assertThat;
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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.repository.MomentumRocRepository;
import org.tradelite.repository.OhlcvRepository;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.repository.RsCrossoverStateRepository;
import org.tradelite.repository.SectorPerformanceRepository;
import org.tradelite.repository.TargetPriceRepository;
import org.tradelite.repository.TrackedSymbolRepository;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.model.RelativeStrengthData;

class DevDataSeederTest {

    @TempDir Path tempDir;

    private FinnhubPriceEvaluator finnhubPriceEvaluator;
    private RsCrossoverStateRepository rsCrossoverStateRepository;

    @BeforeEach
    void setUp() {
        finnhubPriceEvaluator = mock(FinnhubPriceEvaluator.class);
        when(finnhubPriceEvaluator.getLastPriceCache())
                .thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
        rsCrossoverStateRepository = mock(RsCrossoverStateRepository.class);
        when(rsCrossoverStateRepository.findAll()).thenReturn(Map.of());
    }

    @Test
    void reseed_populatesSqliteAndIndicatorState() {
        SQLiteDataSource dataSource = createDataSource("tradebot-dev.db");
        JdbcTemplate jdbcTemplate = createJdbcTemplateWithSchema(dataSource);
        PriceQuoteRepository priceQuoteRepository = mock(PriceQuoteRepository.class);

        MomentumRocRepository momentumRocRepository = mock(MomentumRocRepository.class);
        RelativeStrengthService relativeStrengthService = mock(RelativeStrengthService.class);
        TargetPriceProvider targetPriceProvider = mock(TargetPriceProvider.class);
        SymbolRegistry symbolRegistry = mock(SymbolRegistry.class);
        OhlcvRepository ohlcvRepository = mock(OhlcvRepository.class);
        SectorPerformanceRepository sectorPerformanceRepository =
                mock(SectorPerformanceRepository.class);
        TrackedSymbolRepository trackedSymbolRepository = mock(TrackedSymbolRepository.class);
        TargetPriceRepository targetPriceRepository = mock(TargetPriceRepository.class);

        var rsHistory = new java.util.HashMap<String, RelativeStrengthData>();

        when(relativeStrengthService.getRsHistory()).thenReturn(rsHistory);
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(List.of(new TargetPrice("AAPL", 150.0, 200.0)));
        when(symbolRegistry.fromString("AAPL"))
                .thenReturn(java.util.Optional.of(new StockSymbol("AAPL", "Apple Inc")));
        when(symbolRegistry.isEtf("AAPL")).thenReturn(false);
        when(symbolRegistry.getAllEtfs())
                .thenReturn(
                        java.util.Map.of(
                                "SPY", "S&P 500",
                                "XLK", "Technology"));
        when(symbolRegistry.getStocks()).thenReturn(List.of());

        DevDataSeeder seeder =
                new DevDataSeeder(
                        jdbcTemplate,
                        momentumRocRepository,
                        priceQuoteRepository,
                        relativeStrengthService,
                        rsCrossoverStateRepository,
                        targetPriceProvider,
                        symbolRegistry,
                        ohlcvRepository,
                        finnhubPriceEvaluator,
                        sectorPerformanceRepository,
                        trackedSymbolRepository,
                        targetPriceRepository);

        seeder.reseed();

        assertThat(rsHistory, hasKey("AAPL"));

        verify(rsCrossoverStateRepository, atLeastOnce()).save(any(), any());
        verify(momentumRocRepository, atLeastOnce()).save(any(), any());
        verify(ohlcvRepository, atLeastOnce()).saveAll(anyList());
        verify(priceQuoteRepository, atLeastOnce()).saveAll(anyList());
    }

    @Test
    void seedIfMissing_skipsWhenRsCrossoverStateAndBenchmarkDataAlreadyExist() throws Exception {
        SQLiteDataSource dataSource = createDataSource("preseeded.db");
        JdbcTemplate jdbcTemplate = createJdbcTemplateWithSchema(dataSource);
        preseedBenchmarkData(dataSource);
        PriceQuoteRepository priceQuoteRepository = mock(PriceQuoteRepository.class);

        MomentumRocRepository momentumRocRepository = mock(MomentumRocRepository.class);
        RelativeStrengthService relativeStrengthService = mock(RelativeStrengthService.class);
        TargetPriceProvider targetPriceProvider = mock(TargetPriceProvider.class);
        SymbolRegistry symbolRegistry = mock(SymbolRegistry.class);
        OhlcvRepository ohlcvRepository = mock(OhlcvRepository.class);
        SectorPerformanceRepository sectorPerformanceRepository =
                mock(SectorPerformanceRepository.class);
        TrackedSymbolRepository trackedSymbolRepository = mock(TrackedSymbolRepository.class);
        TargetPriceRepository targetPriceRepository = mock(TargetPriceRepository.class);

        // Simulate existing RS crossover state in DB
        RsCrossoverStateRepository preseededRsRepo = mock(RsCrossoverStateRepository.class);
        RelativeStrengthData existingData = new RelativeStrengthData();
        existingData.setInitialized(true);
        when(preseededRsRepo.findAll()).thenReturn(Map.of("AAPL", existingData));

        when(relativeStrengthService.getRsHistory()).thenReturn(new java.util.HashMap<>());
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());
        when(symbolRegistry.getAllEtfs()).thenReturn(java.util.Map.of("SPY", "S&P 500"));
        when(symbolRegistry.getStocks()).thenReturn(List.of());

        DevDataSeeder seeder =
                new DevDataSeeder(
                        jdbcTemplate,
                        momentumRocRepository,
                        priceQuoteRepository,
                        relativeStrengthService,
                        preseededRsRepo,
                        targetPriceProvider,
                        symbolRegistry,
                        ohlcvRepository,
                        finnhubPriceEvaluator,
                        sectorPerformanceRepository,
                        trackedSymbolRepository,
                        targetPriceRepository);

        assertThat(seeder.seedIfMissing(), is(false));
    }

    @Test
    void run_delegatesToSeedIfMissing() {
        DevDataSeeder seeder = spy(createSeederWithEmptySources());
        doReturn(false).when(seeder).seedIfMissing();

        seeder.run(new DefaultApplicationArguments());

        verify(seeder).seedIfMissing();
    }

    @Test
    void seedIfMissing_reseedsWhenRsCrossoverStateIsEmpty() {
        DevDataSeeder seeder = createSeederWithEmptySources();

        boolean seeded = seeder.seedIfMissing();

        assertThat(seeded, is(true));
    }

    @Test
    void reseed_usesStockRegistryFallbackAndLimitsToFiveNonEtfs() {
        SQLiteDataSource dataSource = createDataSource("fallback.db");
        JdbcTemplate jdbcTemplate = createJdbcTemplateWithSchema(dataSource);
        PriceQuoteRepository priceQuoteRepository = mock(PriceQuoteRepository.class);

        MomentumRocRepository momentumRocRepository = mock(MomentumRocRepository.class);
        RelativeStrengthService relativeStrengthService = mock(RelativeStrengthService.class);
        TargetPriceProvider targetPriceProvider = mock(TargetPriceProvider.class);
        SymbolRegistry symbolRegistry = mock(SymbolRegistry.class);
        OhlcvRepository ohlcvRepository = mock(OhlcvRepository.class);
        SectorPerformanceRepository sectorPerformanceRepository =
                mock(SectorPerformanceRepository.class);
        TrackedSymbolRepository trackedSymbolRepository = mock(TrackedSymbolRepository.class);
        TargetPriceRepository targetPriceRepository = mock(TargetPriceRepository.class);

        var rsHistory = new java.util.HashMap<String, RelativeStrengthData>();

        when(relativeStrengthService.getRsHistory()).thenReturn(rsHistory);
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());
        when(symbolRegistry.getAllEtfs()).thenReturn(java.util.Map.of("SPY", "S&P 500"));
        when(symbolRegistry.getStocks())
                .thenReturn(
                        List.of(
                                new StockSymbol("AAPL", "Apple Inc"),
                                new StockSymbol("MSFT", "Microsoft Corp"),
                                new StockSymbol("NVDA", "NVIDIA Corp"),
                                new StockSymbol("AMZN", "Amazon.com"),
                                new StockSymbol("META", "Meta Platforms"),
                                new StockSymbol("GOOG", "Alphabet Inc")));

        DevDataSeeder seeder =
                new DevDataSeeder(
                        jdbcTemplate,
                        momentumRocRepository,
                        priceQuoteRepository,
                        relativeStrengthService,
                        rsCrossoverStateRepository,
                        targetPriceProvider,
                        symbolRegistry,
                        ohlcvRepository,
                        finnhubPriceEvaluator,
                        sectorPerformanceRepository,
                        trackedSymbolRepository,
                        targetPriceRepository);

        seeder.reseed();

        // RS history should contain non-ETF stocks (limited to 5)
        assertThat(rsHistory, hasKey("AAPL"));
        assertThat(rsHistory.containsKey("GOOG"), is(false));
    }

    private DevDataSeeder createSeederWithEmptySources() {
        SQLiteDataSource dataSource = createDataSource("generic.db");
        JdbcTemplate jdbcTemplate = createJdbcTemplateWithSchema(dataSource);
        PriceQuoteRepository priceQuoteRepository = mock(PriceQuoteRepository.class);

        MomentumRocRepository momentumRocRepository = mock(MomentumRocRepository.class);
        RelativeStrengthService relativeStrengthService = mock(RelativeStrengthService.class);
        TargetPriceProvider targetPriceProvider = mock(TargetPriceProvider.class);
        SymbolRegistry symbolRegistry = mock(SymbolRegistry.class);
        OhlcvRepository ohlcvRepository = mock(OhlcvRepository.class);
        SectorPerformanceRepository sectorPerformanceRepository =
                mock(SectorPerformanceRepository.class);
        TrackedSymbolRepository trackedSymbolRepository = mock(TrackedSymbolRepository.class);
        TargetPriceRepository targetPriceRepository = mock(TargetPriceRepository.class);

        when(relativeStrengthService.getRsHistory()).thenReturn(new java.util.HashMap<>());
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());
        when(symbolRegistry.getAllEtfs()).thenReturn(java.util.Map.of("SPY", "S&P 500"));
        when(symbolRegistry.getStocks()).thenReturn(List.of());

        return new DevDataSeeder(
                jdbcTemplate,
                momentumRocRepository,
                priceQuoteRepository,
                relativeStrengthService,
                rsCrossoverStateRepository,
                targetPriceProvider,
                symbolRegistry,
                ohlcvRepository,
                finnhubPriceEvaluator,
                sectorPerformanceRepository,
                trackedSymbolRepository,
                targetPriceRepository);
    }

    private SQLiteDataSource createDataSource(String dbName) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        Path dbPath = tempDir.resolve(dbName);
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return dataSource;
    }

    private JdbcTemplate createJdbcTemplateWithSchema(SQLiteDataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
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
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS momentum_roc_state (
                    symbol TEXT PRIMARY KEY,
                    previous_roc10 REAL NOT NULL,
                    previous_roc20 REAL NOT NULL,
                    initialized INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS twelvedata_daily_ohlcv (
                    symbol TEXT NOT NULL,
                    date TEXT NOT NULL,
                    open REAL,
                    high REAL,
                    low REAL,
                    close REAL,
                    volume INTEGER,
                    UNIQUE(symbol, date)
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS industry_performance (
                    fetch_date TEXT NOT NULL,
                    industry_name TEXT NOT NULL,
                    daily_change REAL,
                    weekly_perf REAL,
                    monthly_perf REAL,
                    quarterly_perf REAL,
                    half_year_perf REAL,
                    yearly_perf REAL,
                    ytd_perf REAL,
                    PRIMARY KEY (fetch_date, industry_name)
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS target_prices (
                    symbol TEXT NOT NULL,
                    asset_type TEXT NOT NULL,
                    buy_target REAL,
                    sell_target REAL,
                    PRIMARY KEY (symbol, asset_type)
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS tracked_symbols (
                    ticker TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    asset_type TEXT NOT NULL,
                    PRIMARY KEY (ticker, asset_type)
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS rs_crossover_state (
                    symbol TEXT PRIMARY KEY,
                    previous_rs REAL NOT NULL,
                    previous_ema REAL NOT NULL,
                    initialized INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);
        return jdbcTemplate;
    }

    private void preseedBenchmarkData(SQLiteDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    """
                    INSERT INTO finnhub_price_quotes
                    (symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                     change_amount, change_percent, previous_close)
                    VALUES ('SPY', 1, 500.0, 500.0, 501.0, 499.0, 1.0, 0.2, 499.0)
                    """);
        }
    }
}
