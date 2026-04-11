package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.StockSymbolRegistry;

@ExtendWith(MockitoExtension.class)
class EmaTrackerTest {

    @Mock private EmaService emaService;
    @Mock private TelegramGateway telegramClient;
    @Mock private StockSymbolRegistry stockSymbolRegistry;

    private EmaTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new EmaTracker(emaService, telegramClient, stockSymbolRegistry);
    }

    @Test
    void sendDailyReport_sendsMessageWhenAnalysesExist() {
        StockSymbol aapl = new StockSymbol("AAPL", "Apple");
        when(stockSymbolRegistry.getAll()).thenReturn(List.of(aapl));
        when(emaService.analyze("AAPL", "Apple"))
                .thenReturn(
                        Optional.of(
                                new EmaAnalysis(
                                        "AAPL",
                                        "Apple",
                                        150.0,
                                        148.0,
                                        145.0,
                                        140.0,
                                        135.0,
                                        130.0,
                                        0,
                                        EmaSignalType.GREEN)));

        tracker.sendDailyReport();

        verify(telegramClient).sendMessage(anyString());
    }

    @Test
    void sendDailyReport_doesNotSendWhenNoAnalyses() {
        StockSymbol aapl = new StockSymbol("AAPL", "Apple");
        when(stockSymbolRegistry.getAll()).thenReturn(List.of(aapl));
        when(emaService.analyze("AAPL", "Apple")).thenReturn(Optional.empty());

        tracker.sendDailyReport();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void buildReport_containsAllSections() {
        List<EmaAnalysis> analyses =
                List.of(
                        new EmaAnalysis(
                                "AAPL",
                                "Apple",
                                150.0,
                                148.0,
                                145.0,
                                140.0,
                                135.0,
                                130.0,
                                0,
                                EmaSignalType.GREEN),
                        new EmaAnalysis(
                                "MSFT",
                                "Microsoft",
                                300.0,
                                310.0,
                                305.0,
                                320.0,
                                290.0,
                                280.0,
                                3,
                                EmaSignalType.YELLOW),
                        new EmaAnalysis(
                                "TSLA",
                                "Tesla",
                                100.0,
                                110.0,
                                120.0,
                                130.0,
                                140.0,
                                150.0,
                                5,
                                EmaSignalType.RED));

        String report = tracker.buildReport(analyses);

        assertThat(report).contains("*EMA Daily Report*");
        assertThat(report).contains("🟢 *Above EMAs*");
        assertThat(report).contains("🟡 *Mixed*");
        assertThat(report).contains("🔴 *Below All EMAs*");
        assertThat(report).contains("AAPL");
        assertThat(report).contains("MSFT");
        assertThat(report).contains("TSLA");
        assertThat(report).contains("_EMAs: 9, 21, 50, 100, 200 day_");
    }

    @Test
    void buildReport_omitsEmptySections() {
        List<EmaAnalysis> analyses =
                List.of(
                        new EmaAnalysis(
                                "AAPL",
                                "Apple",
                                150.0,
                                148.0,
                                145.0,
                                140.0,
                                135.0,
                                130.0,
                                0,
                                EmaSignalType.GREEN));

        String report = tracker.buildReport(analyses);

        assertThat(report).contains("🟢 *Above EMAs*");
        assertThat(report).doesNotContain("🟡 *Mixed*");
        assertThat(report).doesNotContain("🔴 *Below All EMAs*");
    }

    @Test
    void buildReport_showsSummaryLine() {
        List<EmaAnalysis> analyses =
                List.of(
                        new EmaAnalysis(
                                "AAPL",
                                "Apple",
                                150.0,
                                148.0,
                                145.0,
                                140.0,
                                135.0,
                                130.0,
                                0,
                                EmaSignalType.GREEN),
                        new EmaAnalysis(
                                "TSLA",
                                "Tesla",
                                100.0,
                                110.0,
                                120.0,
                                130.0,
                                140.0,
                                150.0,
                                5,
                                EmaSignalType.RED));

        String report = tracker.buildReport(analyses);

        assertThat(report).contains("2 symbols: 1 🟢 | 0 🟡 | 1 🔴");
    }

    @Test
    void analyzeAllStocks_collectsAnalysesForAllStocks() {
        StockSymbol aapl = new StockSymbol("AAPL", "Apple");
        StockSymbol msft = new StockSymbol("MSFT", "Microsoft");
        when(stockSymbolRegistry.getAll()).thenReturn(List.of(aapl, msft));
        when(emaService.analyze("AAPL", "Apple"))
                .thenReturn(
                        Optional.of(
                                new EmaAnalysis(
                                        "AAPL",
                                        "Apple",
                                        150.0,
                                        148.0,
                                        145.0,
                                        140.0,
                                        135.0,
                                        130.0,
                                        0,
                                        EmaSignalType.GREEN)));
        when(emaService.analyze("MSFT", "Microsoft")).thenReturn(Optional.empty());

        List<EmaAnalysis> results = tracker.analyzeAllStocks();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().symbol()).isEqualTo("AAPL");
    }
}
