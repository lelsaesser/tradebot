package org.tradelite.quant;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.IgnoreReason;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RelativeStrengthService.RsResult;

@SuppressWarnings("SameParameterValue")
@ExtendWith(MockitoExtension.class)
class PullbackBuyTrackerTest {

    @Mock private EmaService emaService;
    @Mock private RelativeStrengthService relativeStrengthService;
    @Mock private VfiService vfiService;
    @Mock private FinnhubPriceEvaluator finnhubPriceEvaluator;
    @Mock private TelegramGateway telegramClient;
    @Mock private SymbolRegistry symbolRegistry;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private FeatureToggleService featureToggleService;

    private PullbackBuyTracker tracker;
    private Map<String, Double> priceCache;

    private static final StockSymbol AAPL = new StockSymbol("AAPL", "Apple Inc");
    private static final StockSymbol MSFT = new StockSymbol("MSFT", "Microsoft");

    @BeforeEach
    void setUp() {
        tracker =
                new PullbackBuyTracker(
                        emaService,
                        relativeStrengthService,
                        vfiService,
                        finnhubPriceEvaluator,
                        telegramClient,
                        symbolRegistry,
                        targetPriceProvider,
                        featureToggleService);
        priceCache = new ConcurrentHashMap<>();
        lenient()
                .when(featureToggleService.isEnabled(FeatureToggle.PULLBACK_BUY_ALERT))
                .thenReturn(true);
        lenient().when(symbolRegistry.getStocks()).thenReturn(List.of());
        lenient().when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(priceCache);
    }

    @Test
    void analyzeAndSendAlerts_featureToggleDisabled_skips() {
        when(featureToggleService.isEnabled(FeatureToggle.PULLBACK_BUY_ALERT)).thenReturn(false);

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
        verify(emaService, never()).analyze(anyString(), anyString());
    }

    @Test
    void analyzeAndSendAlerts_allConditionsMet_sendsAlert() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 178.50);
        mockEma("AAPL", "Apple Inc", 178.50, 180.0, 181.0, 175.0, 170.0, 165.0);
        mockRsPositive("AAPL");
        mockVfiPositive("AAPL", "Apple Inc");

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());
        assertThat(captor.getValue(), containsString("Potential buy for Apple Inc (AAPL)"));
        assertThat(captor.getValue(), containsString("$178.50"));
        assertThat(
                captor.getValue(),
                containsString("21 EMA pullback while volume and relative strength stay bullish"));
        verify(targetPriceProvider).addIgnoredSymbol(AAPL, IgnoreReason.PULLBACK_BUY_ALERT);
    }

    @Test
    void analyzeAndSendAlerts_priceAboveEma9_noAlert() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 181.0);
        // Price 181 > EMA9 180 → not a pullback
        mockEma("AAPL", "Apple Inc", 181.0, 180.0, 179.0, 175.0, 170.0, 165.0);

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_priceBelowEma50_noAlert() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 174.0);
        // Price 174 < EMA50 175 → too deep, not a healthy pullback
        mockEma("AAPL", "Apple Inc", 174.0, 180.0, 181.0, 175.0, 170.0, 165.0);

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_priceBelowEma100_noAlert() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 169.0);
        // Price 169 < EMA100 170 → too deep
        mockEma("AAPL", "Apple Inc", 169.0, 180.0, 181.0, 175.0, 170.0, 165.0);

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_priceBelowEma200_noAlert() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 164.0);
        // Price 164 < EMA200 165 → broken trend
        mockEma("AAPL", "Apple Inc", 164.0, 180.0, 181.0, 175.0, 170.0, 165.0);

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_ema50IsNaN_skipsStock() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 178.50);
        mockEma("AAPL", "Apple Inc", 178.50, 180.0, 181.0, Double.NaN, 170.0, 165.0);

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
        verify(relativeStrengthService, never()).getCurrentRsResult(anyString());
    }

    @Test
    void analyzeAndSendAlerts_ema100IsNaN_skipsStock() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 178.50);
        mockEma("AAPL", "Apple Inc", 178.50, 180.0, 181.0, 175.0, Double.NaN, 165.0);

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_ema200IsNaN_skipsStock() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 178.50);
        mockEma("AAPL", "Apple Inc", 178.50, 180.0, 181.0, 175.0, 170.0, Double.NaN);

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_noCachedPrice_skipsStock() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        // No entry in priceCache for AAPL
        mockEma("AAPL", "Apple Inc", 178.50, 180.0, 181.0, 175.0, 170.0, 165.0);

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
        verify(relativeStrengthService, never()).getCurrentRsResult(anyString());
    }

    @Test
    void analyzeAndSendAlerts_rsNegative_noAlert() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 178.50);
        mockEma("AAPL", "Apple Inc", 178.50, 180.0, 181.0, 175.0, 170.0, 165.0);
        // RS below EMA → underperforming SPY
        when(relativeStrengthService.getCurrentRsResult("AAPL"))
                .thenReturn(Optional.of(new RsResult(0.95, 0.98, 50, true)));

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_rsEmpty_noAlert() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 178.50);
        mockEma("AAPL", "Apple Inc", 178.50, 180.0, 181.0, 175.0, 170.0, 165.0);
        when(relativeStrengthService.getCurrentRsResult("AAPL")).thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_vfiNegative_noAlert() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 178.50);
        mockEma("AAPL", "Apple Inc", 178.50, 180.0, 181.0, 175.0, 170.0, 165.0);
        mockRsPositive("AAPL");
        // VFI negative
        when(vfiService.analyze(eq("AAPL"), anyString()))
                .thenReturn(Optional.of(new VfiAnalysis("AAPL", "Apple Inc", -1.0, -0.5)));

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_vfiEmpty_noAlert() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 178.50);
        mockEma("AAPL", "Apple Inc", 178.50, 180.0, 181.0, 175.0, 170.0, 165.0);
        mockRsPositive("AAPL");
        when(vfiService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_symbolIgnored_skipsEntirely() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        when(targetPriceProvider.isSymbolIgnored(AAPL, IgnoreReason.PULLBACK_BUY_ALERT))
                .thenReturn(true);

        tracker.analyzeAndSendAlerts();

        verify(emaService, never()).analyze(anyString(), anyString());
        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_multipleStocks_sendsSeperateMessages() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL, MSFT));
        priceCache.put("AAPL", 178.50);
        priceCache.put("MSFT", 420.0);
        mockEma("AAPL", "Apple Inc", 178.50, 180.0, 181.0, 175.0, 170.0, 165.0);
        mockEma("MSFT", "Microsoft", 420.0, 425.0, 428.0, 415.0, 400.0, 380.0);
        mockRsPositive("AAPL");
        mockRsPositive("MSFT");
        mockVfiPositive("AAPL", "Apple Inc");
        mockVfiPositive("MSFT", "Microsoft");

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, times(2)).sendMessage(anyString());
        verify(targetPriceProvider).addIgnoredSymbol(AAPL, IgnoreReason.PULLBACK_BUY_ALERT);
        verify(targetPriceProvider).addIgnoredSymbol(MSFT, IgnoreReason.PULLBACK_BUY_ALERT);
    }

    @Test
    void analyzeAndSendAlerts_emaDataUnavailable_skipsStock() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        priceCache.put("AAPL", 178.50);
        when(emaService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
        verify(relativeStrengthService, never()).getCurrentRsResult(anyString());
    }

    @Test
    void isPullbackPattern_allConditionsMet_returnsTrue() {
        EmaAnalysis ema = createEma(178.50, 180.0, 181.0, 175.0, 170.0, 165.0);
        assertThat(PullbackBuyTracker.isPullbackPattern(178.50, ema), is(true));
    }

    @Test
    void isPullbackPattern_priceAboveEma9_returnsFalse() {
        EmaAnalysis ema = createEma(181.0, 180.0, 179.0, 175.0, 170.0, 165.0);
        assertThat(PullbackBuyTracker.isPullbackPattern(181.0, ema), is(false));
    }

    @Test
    void isPullbackPattern_priceAboveEma21ButBelowEma9_returnsFalse() {
        EmaAnalysis ema = createEma(180.0, 181.0, 179.0, 175.0, 170.0, 165.0);
        assertThat(PullbackBuyTracker.isPullbackPattern(180.0, ema), is(false));
    }

    @Test
    void isPullbackPattern_priceBelowEma50_returnsFalse() {
        EmaAnalysis ema = createEma(174.0, 180.0, 181.0, 175.0, 170.0, 165.0);
        assertThat(PullbackBuyTracker.isPullbackPattern(174.0, ema), is(false));
    }

    @Test
    void buildAlertMessage_correctFormat() {
        String message = PullbackBuyTracker.buildAlertMessage(AAPL, 178.50);
        assertThat(
                message,
                is(
                        "Potential buy for Apple Inc (AAPL) at $178.50."
                                + " 21 EMA pullback while volume and relative strength stay bullish"));
    }

    private void mockEma(
            String symbol,
            String displayName,
            double price,
            double ema9,
            double ema21,
            double ema50,
            double ema100,
            double ema200) {
        int available = countAvailable(ema9, ema21, ema50, ema100, ema200);
        int below = countBelow(price, ema9, ema21, ema50, ema100, ema200);
        lenient()
                .when(emaService.analyze(eq(symbol), anyString()))
                .thenReturn(
                        Optional.of(
                                new EmaAnalysis(
                                        symbol,
                                        displayName,
                                        price,
                                        ema9,
                                        ema21,
                                        ema50,
                                        ema100,
                                        ema200,
                                        available,
                                        below,
                                        EmaSignalType.fromEmasBelow(below, available))));
    }

    private EmaAnalysis createEma(
            double price, double ema9, double ema21, double ema50, double ema100, double ema200) {
        int available = countAvailable(ema9, ema21, ema50, ema100, ema200);
        int below = countBelow(price, ema9, ema21, ema50, ema100, ema200);
        return new EmaAnalysis(
                "TEST",
                "Test",
                price,
                ema9,
                ema21,
                ema50,
                ema100,
                ema200,
                available,
                below,
                EmaSignalType.fromEmasBelow(below, available));
    }

    private void mockRsPositive(String symbol) {
        lenient()
                .when(relativeStrengthService.getCurrentRsResult(symbol))
                .thenReturn(Optional.of(new RsResult(1.05, 1.04, 50, true)));
    }

    private void mockVfiPositive(String symbol, String displayName) {
        lenient()
                .when(vfiService.analyze(eq(symbol), anyString()))
                .thenReturn(Optional.of(new VfiAnalysis(symbol, displayName, 3.0, 2.0)));
    }

    private static int countAvailable(double... emas) {
        int count = 0;
        for (double ema : emas) {
            if (!Double.isNaN(ema)) count++;
        }
        return count;
    }

    private static int countBelow(double price, double... emas) {
        int count = 0;
        for (double ema : emas) {
            if (!Double.isNaN(ema) && price < ema) count++;
        }
        return count;
    }
}
