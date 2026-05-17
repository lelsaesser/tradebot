package org.tradelite.core;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.EarningsCalendarResponse;
import org.tradelite.client.finnhub.dto.EarningsCalendarResponse.EarningsEvent;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.repository.TrackedSymbolRepository;
import org.tradelite.service.FeatureToggleService;

@ExtendWith(MockitoExtension.class)
class EarningsCalendarTrackerTest {

    @Mock private FinnhubClient finnhubClient;
    @Mock private TelegramGateway telegramClient;
    @Mock private FeatureToggleService featureToggleService;
    @Mock private TrackedSymbolRepository trackedSymbolRepository;

    private EarningsCalendarTracker tracker;

    @BeforeEach
    void setUp() {
        List<SymbolRegistry.StockSymbolEntry> entries =
                List.of(
                        new SymbolRegistry.StockSymbolEntry("AAPL", "Apple"),
                        new SymbolRegistry.StockSymbolEntry("MSFT", "Microsoft"),
                        new SymbolRegistry.StockSymbolEntry("NVDA", "Nvidia"),
                        new SymbolRegistry.StockSymbolEntry("TSLA", "Tesla"));
        when(trackedSymbolRepository.findAll()).thenReturn(entries);

        SymbolRegistry symbolRegistry = new SymbolRegistry(trackedSymbolRepository);

        tracker =
                new EarningsCalendarTracker(
                        finnhubClient, telegramClient, symbolRegistry, featureToggleService);
    }

    @Test
    void checkAndAlert_featureDisabled_shouldNotCallApi() {
        when(featureToggleService.isEnabled(FeatureToggle.EARNINGS_CALENDAR_ALERT))
                .thenReturn(false);

        tracker.checkAndAlert();

        verify(finnhubClient, never()).getEarningsCalendar(anyString(), anyString());
        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void checkAndAlert_nullResponse_shouldNotSendMessage() {
        when(featureToggleService.isEnabled(FeatureToggle.EARNINGS_CALENDAR_ALERT))
                .thenReturn(true);
        when(finnhubClient.getEarningsCalendar(anyString(), anyString())).thenReturn(null);

        tracker.checkAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void checkAndAlert_noMatchingSymbols_shouldNotSendMessage() {
        when(featureToggleService.isEnabled(FeatureToggle.EARNINGS_CALENDAR_ALERT))
                .thenReturn(true);

        EarningsEvent untracked = createEvent("2026-05-05", "UNKNOWN");
        EarningsCalendarResponse response = new EarningsCalendarResponse();
        response.setEarningsCalendar(List.of(untracked));
        when(finnhubClient.getEarningsCalendar(anyString(), anyString())).thenReturn(response);

        tracker.checkAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void checkAndAlert_matchingSymbols_shouldSendGroupedMessage() {
        when(featureToggleService.isEnabled(FeatureToggle.EARNINGS_CALENDAR_ALERT))
                .thenReturn(true);

        EarningsEvent aapl = createEvent("2026-05-05", "AAPL");
        EarningsEvent msft = createEvent("2026-05-05", "MSFT");
        EarningsEvent nvda = createEvent("2026-05-07", "NVDA");
        EarningsEvent untracked = createEvent("2026-05-06", "UNKNOWN");

        EarningsCalendarResponse response = new EarningsCalendarResponse();
        response.setEarningsCalendar(List.of(aapl, msft, nvda, untracked));
        when(finnhubClient.getEarningsCalendar(anyString(), anyString())).thenReturn(response);

        tracker.checkAndAlert();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());

        String message = captor.getValue();
        assertThat(message, containsString("Earnings Calendar"));
        assertThat(message, containsString("Apple (AAPL)"));
        assertThat(message, containsString("Microsoft (MSFT)"));
        assertThat(message, containsString("Nvidia (NVDA)"));
        assertThat(message, not(containsString("UNKNOWN")));
    }

    @Test
    void checkAndAlert_matchingSymbols_shouldGroupByDateChronologically() {
        when(featureToggleService.isEnabled(FeatureToggle.EARNINGS_CALENDAR_ALERT))
                .thenReturn(true);

        EarningsEvent nvda = createEvent("2026-05-07", "NVDA");
        EarningsEvent aapl = createEvent("2026-05-05", "AAPL");

        EarningsCalendarResponse response = new EarningsCalendarResponse();
        response.setEarningsCalendar(List.of(nvda, aapl));
        when(finnhubClient.getEarningsCalendar(anyString(), anyString())).thenReturn(response);

        tracker.checkAndAlert();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());

        String message = captor.getValue();
        int aaplIndex = message.indexOf("Apple (AAPL)");
        int nvdaIndex = message.indexOf("Nvidia (NVDA)");
        assertThat("AAPL (May 5) should appear before NVDA (May 7)", aaplIndex < nvdaIndex);
    }

    @Test
    void buildAlertMessage_shouldContainDateHeaders() {
        EarningsEvent aapl = createEvent("2026-05-05", "AAPL");
        EarningsEvent tsla = createEvent("2026-05-08", "TSLA");

        String message = tracker.buildAlertMessage(List.of(aapl, tsla));

        assertThat(message, containsString("Tuesday, May 5:"));
        assertThat(message, containsString("Friday, May 8:"));
    }

    @Test
    void checkAndAlert_emptyEarningsList_shouldNotSendMessage() {
        when(featureToggleService.isEnabled(FeatureToggle.EARNINGS_CALENDAR_ALERT))
                .thenReturn(true);

        EarningsCalendarResponse response = new EarningsCalendarResponse();
        response.setEarningsCalendar(List.of());
        when(finnhubClient.getEarningsCalendar(anyString(), anyString())).thenReturn(response);

        tracker.checkAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    private EarningsEvent createEvent(String date, String symbol) {
        EarningsEvent event = new EarningsEvent();
        event.setDate(date);
        event.setSymbol(symbol);
        event.setHour("bmo");
        event.setQuarter(1);
        event.setYear(2026);
        return event;
    }
}
