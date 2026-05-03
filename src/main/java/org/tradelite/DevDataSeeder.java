package org.tradelite;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.client.finviz.dto.IndustryPerformance;
import org.tradelite.common.AssetType;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.SectorPerformanceSnapshot;
import org.tradelite.quant.StatisticsUtil;
import org.tradelite.repository.MomentumRocRepository;
import org.tradelite.repository.OhlcvRepository;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.repository.SectorPerformanceRepository;
import org.tradelite.repository.TargetPriceRepository;
import org.tradelite.repository.TrackedSymbolRepository;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.model.DailyPrice;
import org.tradelite.service.model.MomentumRocData;
import org.tradelite.service.model.RelativeStrengthData;

@SuppressWarnings("SameParameterValue")
@Slf4j
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final int LOOKBACK_DAYS = 90;
    private static final int OHLCV_LOOKBACK_DAYS = 400;
    private static final int SAMPLE_STOCK_LIMIT = 5;
    private static final int SECTOR_PERF_SEED_DAYS = 30;
    private static final LocalTime SEEDED_CLOSE_TIME = LocalTime.of(20, 0);
    private static final List<String> SEEDED_INDUSTRIES =
            List.of(
                    "Technology",
                    "Healthcare",
                    "Energy",
                    "Financials",
                    "Consumer Cyclical",
                    "Industrials",
                    "Utilities",
                    "Real Estate");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MomentumRocRepository momentumRocRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final RelativeStrengthService relativeStrengthService;
    private final TargetPriceProvider targetPriceProvider;
    private final SymbolRegistry symbolRegistry;
    private final OhlcvRepository ohlcvRepository;
    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final SectorPerformanceRepository sectorPerformanceRepository;
    private final TrackedSymbolRepository trackedSymbolRepository;
    private final TargetPriceRepository targetPriceRepository;
    private final Path rsDataFilePath;

    @Autowired
    public DevDataSeeder(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            MomentumRocRepository momentumRocRepository,
            PriceQuoteRepository priceQuoteRepository,
            RelativeStrengthService relativeStrengthService,
            TargetPriceProvider targetPriceProvider,
            SymbolRegistry symbolRegistry,
            OhlcvRepository ohlcvRepository,
            FinnhubPriceEvaluator finnhubPriceEvaluator,
            SectorPerformanceRepository sectorPerformanceRepository,
            TrackedSymbolRepository trackedSymbolRepository,
            TargetPriceRepository targetPriceRepository) {
        this(
                jdbcTemplate,
                objectMapper,
                momentumRocRepository,
                priceQuoteRepository,
                relativeStrengthService,
                targetPriceProvider,
                symbolRegistry,
                ohlcvRepository,
                finnhubPriceEvaluator,
                sectorPerformanceRepository,
                trackedSymbolRepository,
                targetPriceRepository,
                Path.of("config/rs-data.json"));
    }

    DevDataSeeder(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            MomentumRocRepository momentumRocRepository,
            PriceQuoteRepository priceQuoteRepository,
            RelativeStrengthService relativeStrengthService,
            TargetPriceProvider targetPriceProvider,
            SymbolRegistry symbolRegistry,
            OhlcvRepository ohlcvRepository,
            FinnhubPriceEvaluator finnhubPriceEvaluator,
            SectorPerformanceRepository sectorPerformanceRepository,
            TrackedSymbolRepository trackedSymbolRepository,
            TargetPriceRepository targetPriceRepository,
            Path rsDataFilePath) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.momentumRocRepository = momentumRocRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.relativeStrengthService = relativeStrengthService;
        this.targetPriceProvider = targetPriceProvider;
        this.symbolRegistry = symbolRegistry;
        this.ohlcvRepository = ohlcvRepository;
        this.finnhubPriceEvaluator = finnhubPriceEvaluator;
        this.sectorPerformanceRepository = sectorPerformanceRepository;
        this.trackedSymbolRepository = trackedSymbolRepository;
        this.targetPriceRepository = targetPriceRepository;
        this.rsDataFilePath = rsDataFilePath;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
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
        clearExistingData();
        seedStockSymbolsAndTargetPrices();
        symbolRegistry.reload();
        SeedBundle bundle = buildSeedBundle();
        try {
            insertPriceHistory(bundle.priceSeriesBySymbol());
            seedRelativeStrengthHistory(bundle);
            seedMomentumState(bundle);
            seedOhlcvData(bundle);
            seedPriceCache(bundle);
            seedSectorPerformance();
            log.info(
                    "Seeded dev analytics data for {} symbols",
                    bundle.priceSeriesBySymbol().size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to seed dev analytics data", e);
        }
    }

    private boolean hasSeedData() {
        return Files.exists(rsDataFilePath) && hasPersistedQuotes(SymbolRegistry.BENCHMARK_SYMBOL);
    }

    private boolean hasPersistedQuotes(String symbol) {
        try {
            Integer count =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM finnhub_price_quotes"
                                    + " WHERE symbol = ? AND current_price > 0",
                            Integer.class,
                            symbol);
            return count != null && count > 0;
        } catch (Exception _) {
            return false;
        }
    }

    private void clearExistingData() {
        jdbcTemplate.update("DELETE FROM finnhub_price_quotes");
        jdbcTemplate.update("DELETE FROM momentum_roc_state");
        jdbcTemplate.update("DELETE FROM twelvedata_daily_ohlcv");
        jdbcTemplate.update("DELETE FROM industry_performance");
        jdbcTemplate.update("DELETE FROM target_prices");
        jdbcTemplate.update("DELETE FROM tracked_symbols");

        try {
            Files.deleteIfExists(rsDataFilePath);
        } catch (IOException e) {
            log.warn("Failed to delete RS data file: {}", rsDataFilePath, e);
        }
        relativeStrengthService.getRsHistory().clear();
    }

    private void seedStockSymbolsAndTargetPrices() {
        List<String[]> sampleStocks =
                List.of(
                        new String[] {"AAPL", "Apple"},
                        new String[] {"MSFT", "Microsoft"},
                        new String[] {"GOOG", "Google"},
                        new String[] {"TSLA", "Tesla"},
                        new String[] {"META", "Meta"},
                        new String[] {"NVDA", "Nvidia"},
                        new String[] {"RKLB", "Rocket Lab"},
                        new String[] {"PLTR", "Palantir"},
                        new String[] {"AVGO", "Broadcom"},
                        new String[] {"GLXY", "Galaxy Digital"},
                        new String[] {"MP", "MP Materials"},
                        new String[] {"ASML", "ASML"},
                        new String[] {"TEM", "Tempus AI"},
                        new String[] {"TSM", "Taiwan Semiconductor Manufacturing"},
                        new String[] {"MU", "Western Digital"},
                        new String[] {"ORCL", "Oracle"},
                        new String[] {"INTC", "Intel"},
                        new String[] {"DELL", "Dell"},
                        new String[] {"CRWV", "CoreWeave"},
                        new String[] {"IREN", "Iris Energy"},
                        new String[] {"NBIS", "Nebius Group"},
                        new String[] {"COHR", "Coherent Corp"},
                        new String[] {"CAT", "Caterpillar"},
                        new String[] {"KLAC", "KLA Corp"},
                        new String[] {"LITE", "Lumentum Holdings"},
                        new String[] {"TER", "Teradyne"},
                        new String[] {"AMAT", "Applied Materials"},
                        new String[] {"LRCX", "Lam Research"},
                        new String[] {"SNDK", "SanDisk"},
                        new String[] {"WDC", "Western Digital"},
                        new String[] {"AXTI", "AXT Inc"},
                        new String[] {"SPY", "S&P 500 ETF"},
                        new String[] {"SMH", "Semiconductor Sector ETF"},
                        new String[] {"GEV", "GE Vernova"},
                        new String[] {"XLK", "Technology Sector"},
                        new String[] {"XLF", "Finance Sector"},
                        new String[] {"XLE", "Energy Sector"},
                        new String[] {"XLV", "Healthcare Sector"},
                        new String[] {"XLY", "Consumer Discretionary Sector"},
                        new String[] {"XLP", "Consumer Staples Sector"},
                        new String[] {"XLI", "Industrial Sector"},
                        new String[] {"XLC", "Communication Services Sector"},
                        new String[] {"XLRE", "Real Estate Sector"},
                        new String[] {"XLB", "Materials Sector"},
                        new String[] {"XLU", "Utilities Sector"},
                        new String[] {"BTSG", "BrightSpring Health Services"},
                        new String[] {"PL", "Planet Labs"},
                        new String[] {"FET", "Forum Energy Technologies"},
                        new String[] {"FTI", "TechnipFMC"},
                        new String[] {"EFXT", "Enerflex"},
                        new String[] {"FIX", "Comfort Systems USA"},
                        new String[] {"GLW", "Corning Incorporated"},
                        new String[] {"CIEN", "Ciena Corporation"},
                        new String[] {"MPWR", "Monolithic Power Systems"},
                        new String[] {"NFLX", "Netflix"},
                        new String[] {"HOOD", "Robinhood"},
                        new String[] {"AES", "AES Corporation"},
                        new String[] {"NEE", "NextEra Energy"},
                        new String[] {"SIMO", "Silicon Motion Technology"},
                        new String[] {"KEYS", "Keysight Technologies"},
                        new String[] {"MKSI", "MKS Inc."},
                        new String[] {"VPG", "Vishay Precision Group"},
                        new String[] {"ITRN", "Ituran Location and Control"},
                        new String[] {"SPHR", "Sphere Entertainment"},
                        new String[] {"SPOT", "Spotify"},
                        new String[] {"BE", "Bloom Energy"},
                        new String[] {"SHLD", "Defense Tech ETF"},
                        new String[] {"AAOI", "Applied Optoelectronics"},
                        new String[] {"URA", "Uranium ETF"},
                        new String[] {"LINC", "Lincoln Educational Services"},
                        new String[] {"TSEM", "Tower Semiconductor"},
                        new String[] {"IGV", "Software ETF"},
                        new String[] {"XOP", "Oil & Gas Sector"},
                        new String[] {"XHB", "Homebuilders ETF"},
                        new String[] {"ITA", "Aerospace & Defence ETF"},
                        new String[] {"XBI", "Biotech ETF"},
                        new String[] {"UFO", "Procure Space ETF"},
                        new String[] {"TAN", "Solar ETF"},
                        new String[] {"XOM", "Exxon Mobil"},
                        new String[] {"CVX", "Chevron"},
                        new String[] {"PANW", "Palo Alto Networks"},
                        new String[] {"REMX", "Rare Earths ETF"},
                        new String[] {"QTUM", "Quantum ETF"},
                        new String[] {"DTCR", "Data Centers ETF"},
                        new String[] {"FINX", "FinTech ETF"},
                        new String[] {"LIT", "Batteries ETF"},
                        new String[] {"BOTZ", "Robotics ETF"},
                        new String[] {"STCE", "Crypto ETF"},
                        new String[] {"MRVL", "Marvell Technology"});

        for (String[] stock : sampleStocks) {
            trackedSymbolRepository.save(stock[0], stock[1], AssetType.STOCK);
        }

        // Seed target prices for first 5 stocks only (for price alert testing)
        for (int i = 0; i < 5; i++) {
            targetPriceRepository.save(
                    new TargetPrice(sampleStocks.get(i)[0], 150.0, 250.0), AssetType.STOCK);
        }

        targetPriceRepository.save(new TargetPrice("BITCOIN", 100000.0, 0.0), AssetType.COIN);
        targetPriceRepository.save(new TargetPrice("ETHEREUM", 2000.0, 0.0), AssetType.COIN);

        log.info("Seeded {} stock symbols and {} target prices", sampleStocks.size(), 5 + 2);
    }

    private SeedBundle buildSeedBundle() {
        Map<String, String> displayNames = new LinkedHashMap<>(symbolRegistry.getAllEtfs());

        int stockCount = 0;
        for (TargetPrice targetPrice : targetPriceProvider.getStockTargetPrices()) {
            if (stockCount >= SAMPLE_STOCK_LIMIT) {
                break;
            }

            symbolRegistry
                    .fromString(targetPrice.getSymbol())
                    .filter(symbol -> !symbolRegistry.isEtf(symbol.getTicker()))
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
            for (StockSymbol stockSymbol : symbolRegistry.getStocks()) {
                if (stockCount >= SAMPLE_STOCK_LIMIT) {
                    break;
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
                                    StatisticsUtil.roundTo2Decimals(
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

    private void insertPriceHistory(Map<String, List<DailyPrice>> priceSeriesBySymbol) {
        List<PriceQuoteResponse> allQuotes = new ArrayList<>();

        for (Map.Entry<String, List<DailyPrice>> entry : priceSeriesBySymbol.entrySet()) {
            List<DailyPrice> series = entry.getValue();
            for (int index = 0; index < series.size(); index++) {
                DailyPrice current = series.get(index);
                double previousClose =
                        index == 0
                                ? StatisticsUtil.roundTo2Decimals(current.getPrice() * 0.99)
                                : series.get(index - 1).getPrice();
                double open =
                        StatisticsUtil.roundTo2Decimals((current.getPrice() + previousClose) / 2.0);
                double high =
                        StatisticsUtil.roundTo2Decimals(Math.max(open, current.getPrice()) * 1.01);
                double low =
                        StatisticsUtil.roundTo2Decimals(Math.min(open, current.getPrice()) * 0.99);
                double change = StatisticsUtil.roundTo2Decimals(current.getPrice() - previousClose);
                double changePercent =
                        previousClose == 0.0
                                ? 0.0
                                : StatisticsUtil.roundTo2Decimals((change / previousClose) * 100.0);
                long timestamp =
                        current.getDate().atTime(SEEDED_CLOSE_TIME).toEpochSecond(ZoneOffset.UTC);

                PriceQuoteResponse quote = new PriceQuoteResponse();
                quote.setStockSymbol(new StockSymbol(entry.getKey(), entry.getKey()));
                quote.setTimestamp(timestamp);
                quote.setCurrentPrice(current.getPrice());
                quote.setDailyOpen(open);
                quote.setDailyHigh(high);
                quote.setDailyLow(low);
                quote.setChange(change);
                quote.setChangePercent(changePercent);
                quote.setPreviousClose(previousClose);
                allQuotes.add(quote);
            }
        }

        priceQuoteRepository.saveAll(allQuotes);
    }

    private void seedRelativeStrengthHistory(SeedBundle bundle) throws IOException {
        Map<String, RelativeStrengthData> rsHistory = relativeStrengthService.getRsHistory();
        rsHistory.clear();

        List<DailyPrice> spySeries =
                bundle.priceSeriesBySymbol().get(SymbolRegistry.BENCHMARK_SYMBOL);
        Map<LocalDate, Double> spyByDate = new LinkedHashMap<>();
        for (DailyPrice dailyPrice : spySeries) {
            spyByDate.put(dailyPrice.getDate(), dailyPrice.getPrice());
        }

        for (Map.Entry<String, List<DailyPrice>> entry : bundle.priceSeriesBySymbol().entrySet()) {
            if (SymbolRegistry.BENCHMARK_SYMBOL.equals(entry.getKey())) {
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
            rsData.setPreviousRs(rsValues.getLast());
            rsData.setPreviousEma(StatisticsUtil.calculateEma(rsValues, emaPeriod));
            rsData.setInitialized(true);
            rsHistory.put(entry.getKey(), rsData);
        }

        ensureParentDirectories(rsDataFilePath);
        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(rsDataFilePath.toFile(), rsHistory);
    }

    private void seedMomentumState(SeedBundle bundle) {
        for (String symbol : symbolRegistry.getAllEtfs().keySet()) {
            List<DailyPrice> series = bundle.priceSeriesBySymbol().get(symbol);
            if (series == null || series.size() <= 20) {
                continue;
            }

            MomentumRocData momentumRocData = new MomentumRocData();
            momentumRocData.setPreviousRoc10(StatisticsUtil.calculateRocValue(series, 10));
            momentumRocData.setPreviousRoc20(StatisticsUtil.calculateRocValue(series, 20));
            momentumRocData.setInitialized(true);
            momentumRocRepository.save(symbol, momentumRocData);
        }
    }

    private void seedOhlcvData(SeedBundle bundle) {
        int count = 0;
        for (Map.Entry<String, String> entry : bundle.displayNamesBySymbol().entrySet()) {
            String symbol = entry.getKey();
            List<OhlcvRecord> records = buildOhlcvSeries(symbol);
            ohlcvRepository.saveAll(records);
            count++;
        }
        log.info("Seeded OHLCV data for {} symbols ({} days each)", count, OHLCV_LOOKBACK_DAYS);
    }

    private void seedPriceCache(SeedBundle bundle) {
        Map<String, Double> cache = finnhubPriceEvaluator.getLastPriceCache();
        for (Map.Entry<String, List<DailyPrice>> entry : bundle.priceSeriesBySymbol().entrySet()) {
            List<DailyPrice> series = entry.getValue();
            if (!series.isEmpty()) {
                cache.put(entry.getKey(), series.getLast().getPrice());
            }
        }

        // Set one stock's cached price to a pullback level (below EMA 9/21, above EMA 50)
        // so the pullback buy alert can fire in dev mode
        seedPullbackPrice(bundle, cache);

        log.info("Seeded price cache for {} symbols", cache.size());
    }

    private void seedPullbackPrice(SeedBundle bundle, Map<String, Double> cache) {
        for (StockSymbol stock : symbolRegistry.getStocks()) {
            if (!bundle.displayNamesBySymbol().containsKey(stock.getTicker())) {
                continue;
            }

            // Use the OHLCV series (400 days) — same data that EmaService.analyze() will read
            List<OhlcvRecord> ohlcvRecords = buildOhlcvSeries(stock.getTicker());
            if (ohlcvRecords.size() < 200) {
                continue;
            }

            List<Double> prices = ohlcvRecords.stream().map(OhlcvRecord::close).toList();
            double ema9 = StatisticsUtil.calculateEma(prices, 9);
            double ema21 = StatisticsUtil.calculateEma(prices, 21);
            double ema50 = StatisticsUtil.calculateEma(prices, 50);
            double ema100 = StatisticsUtil.calculateEma(prices, 100);
            double ema200 = StatisticsUtil.calculateEma(prices, 200);

            if (ema50 < ema21 && ema100 < ema50 && ema200 < ema100) {
                // Place price between EMA 21 and EMA 50 — below short-term, above long-term
                double pullbackPrice = StatisticsUtil.roundTo2Decimals((ema21 + ema50) / 2.0);
                cache.put(stock.getTicker(), pullbackPrice);
                log.info(
                        "Seeded pullback price for {}: ${} (ema9={}, ema21={}, ema50={}, ema100={},"
                                + " ema200={})",
                        stock.getTicker(),
                        pullbackPrice,
                        StatisticsUtil.roundTo2Decimals(ema9),
                        StatisticsUtil.roundTo2Decimals(ema21),
                        StatisticsUtil.roundTo2Decimals(ema50),
                        StatisticsUtil.roundTo2Decimals(ema100),
                        StatisticsUtil.roundTo2Decimals(ema200));
                return;
            }
        }
        log.info("No stock with a valid pullback pattern found for seeding");
    }

    private void seedSectorPerformance() {
        for (int day = SECTOR_PERF_SEED_DAYS - 1; day >= 0; day--) {
            int dayOffset = day;
            LocalDate date = LocalDate.now().minusDays(day);
            List<IndustryPerformance> performances =
                    SEEDED_INDUSTRIES.stream()
                            .map(name -> buildIndustryPerformance(name, dayOffset))
                            .toList();
            sectorPerformanceRepository.saveSnapshot(
                    new SectorPerformanceSnapshot(date, performances));
        }
        log.info(
                "Seeded sector performance data: {} days, {} industries",
                SECTOR_PERF_SEED_DAYS,
                SEEDED_INDUSTRIES.size());
    }

    private IndustryPerformance buildIndustryPerformance(String industryName, int dayOffset) {
        int hash = Math.abs(industryName.hashCode());
        double base = ((hash % 20) - 10) * 0.5;
        double variation = Math.sin((dayOffset + hash % 13) / 4.0) * 3.0;

        double daily = StatisticsUtil.roundTo2Decimals(base + variation);
        double weekly = StatisticsUtil.roundTo2Decimals(daily * 2.5);
        double monthly = StatisticsUtil.roundTo2Decimals(daily * 5.0);
        double quarterly = StatisticsUtil.roundTo2Decimals(daily * 8.0);
        double halfYear = StatisticsUtil.roundTo2Decimals(daily * 12.0);
        double yearly = StatisticsUtil.roundTo2Decimals(daily * 18.0);
        double ytd = StatisticsUtil.roundTo2Decimals(daily * 10.0);

        return new IndustryPerformance(
                industryName,
                BigDecimal.valueOf(weekly),
                BigDecimal.valueOf(monthly),
                BigDecimal.valueOf(quarterly),
                BigDecimal.valueOf(halfYear),
                BigDecimal.valueOf(yearly),
                BigDecimal.valueOf(ytd),
                BigDecimal.valueOf(daily));
    }

    private List<OhlcvRecord> buildOhlcvSeries(String symbol) {
        int hash = Math.abs(symbol.hashCode());
        double basePrice = 80.0 + (hash % 120);
        double dailySlope = ((hash % 11) - 5) * 0.18;
        double amplitude = 3.0 + (hash % 7);
        double phase = (hash % 13) / 3.0;
        long baseVolume = 1_000_000L + (hash % 49_000_000);

        List<OhlcvRecord> records = new ArrayList<>(OHLCV_LOOKBACK_DAYS);
        double previousClose = 0;

        for (int i = 0; i < OHLCV_LOOKBACK_DAYS; i++) {
            LocalDate date = LocalDate.now().minusDays(OHLCV_LOOKBACK_DAYS - 1L - i);
            double seasonal =
                    Math.sin((i + phase) / 5.0) * amplitude
                            + Math.cos((i + phase) / 11.0) * (amplitude / 2.0);
            double close =
                    StatisticsUtil.roundTo2Decimals(
                            Math.max(15.0, basePrice + (i * dailySlope) + seasonal));

            double open =
                    i == 0
                            ? StatisticsUtil.roundTo2Decimals(close * 0.995)
                            : StatisticsUtil.roundTo2Decimals((close + previousClose) / 2.0);
            double high = StatisticsUtil.roundTo2Decimals(Math.max(open, close) * 1.01);
            double low = StatisticsUtil.roundTo2Decimals(Math.min(open, close) * 0.99);
            double volumeVariation = 1.0 + 0.3 * Math.sin(i * 0.7 + phase);
            long volume = (long) (baseVolume * volumeVariation);

            records.add(new OhlcvRecord(symbol, date, open, high, low, close, volume));
            previousClose = close;
        }

        return records;
    }

    private void ensureParentDirectories(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    record SeedBundle(
            Map<String, String> displayNamesBySymbol,
            Map<String, List<DailyPrice>> priceSeriesBySymbol) {}
}
