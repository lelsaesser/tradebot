package org.tradelite.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
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
import org.tradelite.quant.EmaAnalysis;
import org.tradelite.quant.EmaService;
import org.tradelite.quant.EmaSignalType;
import org.tradelite.quant.TrendDirection;
import org.tradelite.quant.VfiAnalysis;
import org.tradelite.quant.VfiService;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RsTrendResult;

@ExtendWith(MockitoExtension.class)
class AccumulationDetectionTrackerTest {

    @Mock private EmaService emaService;
    @Mock private VfiService vfiService;
    @Mock private RelativeStrengthService relativeStrengthService;
    @Mock private AccumulationDetectionService accumulationDetectionService;
    @Mock private TelegramGateway telegramClient;
    @Mock private SymbolRegistry symbolRegistry;
    @Mock private FeatureToggleService featureToggleService;

    private AccumulationDetectionTracker tracker;

    private static final StockSymbol AAPL = new StockSymbol("AAPL", "Apple Inc");
    private static final StockSymbol NVDA = new StockSymbol("NVDA", "Nvidia");
    private static final StockSymbol SPY = new StockSymbol("SPY", "S&P 500");

    @BeforeEach
    void setUp() {
        tracker =
                new AccumulationDetectionTracker(
                        emaService,
                        vfiService,
                        relativeStrengthService,
                        accumulationDetectionService,
                        telegramClient,
                        symbolRegistry,
                        featureToggleService);
        lenient()
                .when(featureToggleService.isEnabled(FeatureToggle.ACCUMULATION_DETECTION))
                .thenReturn(true);
        lenient().when(symbolRegistry.getStocks()).thenReturn(List.of());
    }

    @Test
    void analyzeAndSendAlerts_featureToggleDisabled_skips() {
        when(featureToggleService.isEnabled(FeatureToggle.ACCUMULATION_DETECTION))
                .thenReturn(false);

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
        verify(emaService, never()).analyze(anyString(), anyString());
    }

    @Test
    void analyzeAndSendAlerts_signalDetected_sendsConsolidatedMessage() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        EmaAnalysis ema = createEma("AAPL", "Apple Inc", 100.0, 98.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple Inc", 3.5, 2.0);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.RISING, TrendDirection.RISING);

        when(emaService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(ema));
        when(vfiService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(vfi));
        when(relativeStrengthService.getRsTrend("AAPL")).thenReturn(Optional.of(rsTrend));
        when(accumulationDetectionService.evaluate("AAPL", "Apple Inc", ema, vfi, rsTrend))
                .thenReturn(
                        Optional.of(
                                new AccumulationSignal(
                                        "AAPL",
                                        "Apple Inc",
                                        100.0,
                                        98.0,
                                        102.0,
                                        3.5,
                                        2.0,
                                        1.05,
                                        1.03,
                                        TrendDirection.RISING,
                                        TrendDirection.RISING)));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());
        String message = captor.getValue();
        assertThat(message).contains("*Institutional accumulation detected*");
        assertThat(message).contains("*Apple Inc (AAPL)*");
        assertThat(message).contains("EMA9:");
        assertThat(message).contains("VFI:");
        assertThat(message).contains("RS vs SPY:");
        assertThat(message).contains("\u2191"); // rising arrow
    }

    @Test
    void analyzeAndSendAlerts_noSignals_noMessage() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        EmaAnalysis ema = createEma("AAPL", "Apple Inc", 100.0, 104.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple Inc", 3.5, 2.0);

        when(emaService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(ema));
        when(vfiService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(vfi));
        when(relativeStrengthService.getRsTrend("AAPL")).thenReturn(Optional.empty());
        when(accumulationDetectionService.evaluate("AAPL", "Apple Inc", ema, vfi, null))
                .thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_emaEmpty_warnsAndSkips() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        when(emaService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
        verify(vfiService, never()).analyze(anyString(), anyString());
    }

    @Test
    void analyzeAndSendAlerts_vfiEmpty_warnsAndSkips() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        EmaAnalysis ema = createEma("AAPL", "Apple Inc", 100.0, 98.0, 102.0);
        when(emaService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(ema));
        when(vfiService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
        verify(accumulationDetectionService, never())
                .evaluate(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void analyzeAndSendAlerts_rsEmpty_stillEvaluatesWithNull() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL));
        EmaAnalysis ema = createEma("AAPL", "Apple Inc", 100.0, 98.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple Inc", 3.5, 2.0);

        when(emaService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(ema));
        when(vfiService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(vfi));
        when(relativeStrengthService.getRsTrend("AAPL")).thenReturn(Optional.empty());
        when(accumulationDetectionService.evaluate("AAPL", "Apple Inc", ema, vfi, null))
                .thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        verify(accumulationDetectionService).evaluate("AAPL", "Apple Inc", ema, vfi, null);
    }

    @Test
    void analyzeAndSendAlerts_skipsSpy() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(SPY, AAPL));
        EmaAnalysis ema = createEma("AAPL", "Apple Inc", 100.0, 98.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple Inc", 3.5, 2.0);

        when(emaService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(ema));
        when(vfiService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(vfi));
        when(relativeStrengthService.getRsTrend("AAPL")).thenReturn(Optional.empty());
        when(accumulationDetectionService.evaluate("AAPL", "Apple Inc", ema, vfi, null))
                .thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        verify(emaService, never()).analyze(eq("SPY"), anyString());
    }

    @Test
    void analyzeAndSendAlerts_multipleSignals_singleConsolidatedMessage() {
        when(symbolRegistry.getStocks()).thenReturn(List.of(AAPL, NVDA));

        EmaAnalysis emaAapl = createEma("AAPL", "Apple Inc", 100.0, 98.0, 102.0);
        VfiAnalysis vfiAapl = new VfiAnalysis("AAPL", "Apple Inc", 3.5, 2.0);
        EmaAnalysis emaNvda = createEma("NVDA", "Nvidia", 800.0, 790.0, 810.0);
        VfiAnalysis vfiNvda = new VfiAnalysis("NVDA", "Nvidia", 5.0, 3.0);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.RISING, TrendDirection.FLAT);

        when(emaService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(emaAapl));
        when(vfiService.analyze(eq("AAPL"), anyString())).thenReturn(Optional.of(vfiAapl));
        when(relativeStrengthService.getRsTrend("AAPL")).thenReturn(Optional.of(rsTrend));
        when(accumulationDetectionService.evaluate("AAPL", "Apple Inc", emaAapl, vfiAapl, rsTrend))
                .thenReturn(
                        Optional.of(
                                new AccumulationSignal(
                                        "AAPL",
                                        "Apple Inc",
                                        100.0,
                                        98.0,
                                        102.0,
                                        3.5,
                                        2.0,
                                        1.05,
                                        1.03,
                                        TrendDirection.RISING,
                                        TrendDirection.FLAT)));

        when(emaService.analyze(eq("NVDA"), anyString())).thenReturn(Optional.of(emaNvda));
        when(vfiService.analyze(eq("NVDA"), anyString())).thenReturn(Optional.of(vfiNvda));
        when(relativeStrengthService.getRsTrend("NVDA")).thenReturn(Optional.of(rsTrend));
        when(accumulationDetectionService.evaluate("NVDA", "Nvidia", emaNvda, vfiNvda, rsTrend))
                .thenReturn(
                        Optional.of(
                                new AccumulationSignal(
                                        "NVDA",
                                        "Nvidia",
                                        800.0,
                                        790.0,
                                        810.0,
                                        5.0,
                                        3.0,
                                        1.05,
                                        1.03,
                                        TrendDirection.RISING,
                                        TrendDirection.FLAT)));

        tracker.analyzeAndSendAlerts();

        // Single message, not two
        verify(telegramClient, times(1)).sendMessage(anyString());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());
        String message = captor.getValue();
        assertThat(message).contains("*Apple Inc (AAPL)*");
        assertThat(message).contains("*Nvidia (NVDA)*");
    }

    @Test
    void trendArrow_mapsCorrectly() {
        assertThat(AccumulationDetectionTracker.trendArrow(TrendDirection.RISING))
                .isEqualTo("\u2191");
        assertThat(AccumulationDetectionTracker.trendArrow(TrendDirection.FLAT))
                .isEqualTo("\u2192");
        assertThat(AccumulationDetectionTracker.trendArrow(TrendDirection.FALLING))
                .isEqualTo("\u2193");
    }

    @Test
    void buildAlertMessage_formatsCorrectly() {
        List<AccumulationSignal> signals =
                List.of(
                        new AccumulationSignal(
                                "AAPL",
                                "Apple Inc",
                                112.40,
                                111.80,
                                115.20,
                                3.42,
                                2.18,
                                1.0523,
                                1.0480,
                                TrendDirection.RISING,
                                TrendDirection.RISING));

        String message = tracker.buildAlertMessage(signals);

        assertThat(message).startsWith("*Institutional accumulation detected*");
        assertThat(message).contains("*Apple Inc (AAPL)*");
        assertThat(message).contains("Price: $112.40");
        assertThat(message).contains("EMA9: $111.80 < EMA21: $115.20");
        assertThat(message).contains("VFI: +3.42 | Signal: +2.18");
        assertThat(message).contains("RS vs SPY: 1.0523 \u2191 | EMA: 1.0480 \u2191");
        assertThat(message).contains("_Based on EMA crossdown + positive rising VFI_");
    }

    private EmaAnalysis createEma(
            String symbol, String displayName, double price, double ema9, double ema21) {
        return new EmaAnalysis(
                symbol,
                displayName,
                price,
                ema9,
                ema21,
                95.0,
                90.0,
                85.0,
                5,
                0,
                EmaSignalType.GREEN);
    }
}
