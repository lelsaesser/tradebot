package org.tradelite.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.Exchange;
import org.tradelite.service.MarketStatusService;

@ExtendWith(MockitoExtension.class)
class MarketHolidayNotifierTest {

    @Mock private MarketStatusService marketStatusService;
    @Mock private TelegramGateway telegramClient;

    private MarketHolidayNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new MarketHolidayNotifier(marketStatusService, telegramClient);
    }

    @Test
    void sendDailyReport_fullClose_sendsMessage() {
        MarketHolidayResponse.MarketHoliday holiday = new MarketHolidayResponse.MarketHoliday();
        holiday.setEventName("Memorial Day");
        holiday.setTradingHour("");
        when(marketStatusService.getTodayHoliday()).thenReturn(Optional.of(holiday));
        when(marketStatusService.getTodayInternationalHolidays()).thenReturn(Map.of());

        notifier.sendDailyReport();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String msg = messageCaptor.getValue();
        assertTrue(msg.contains("*Markets closed today*"));
        assertTrue(msg.contains("🇺🇸 NYSE"));
        assertTrue(msg.contains("Memorial Day"));
    }

    @Test
    void sendDailyReport_earlyClose_sendsMessage() {
        MarketHolidayResponse.MarketHoliday holiday = new MarketHolidayResponse.MarketHoliday();
        holiday.setEventName("Day After Thanksgiving");
        holiday.setTradingHour("09:30-13:00");
        when(marketStatusService.getTodayHoliday()).thenReturn(Optional.of(holiday));
        when(marketStatusService.getTodayInternationalHolidays()).thenReturn(Map.of());

        notifier.sendDailyReport();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String msg = messageCaptor.getValue();
        assertTrue(msg.contains("*Markets closed today*"));
        assertTrue(msg.contains("🇺🇸 NYSE"));
        assertTrue(msg.contains("Day After Thanksgiving"));
        assertTrue(msg.contains("early close 13:00 ET"));
    }

    @Test
    void sendDailyReport_noHoliday_sendsNoMessage() {
        when(marketStatusService.getTodayHoliday()).thenReturn(Optional.empty());
        when(marketStatusService.getTodayInternationalHolidays()).thenReturn(Map.of());

        notifier.sendDailyReport();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void sendDailyReport_internationalHolidays_sendsConsolidatedMessage() {
        when(marketStatusService.getTodayHoliday()).thenReturn(Optional.empty());
        when(marketStatusService.getTodayInternationalHolidays())
                .thenReturn(
                        Map.of(
                                Exchange.XETRA, "Tag der Arbeit",
                                Exchange.PAR, "Fête du Travail"));

        notifier.sendDailyReport();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String msg = messageCaptor.getValue();
        assertTrue(msg.contains("*Markets closed today*"));
        assertTrue(msg.contains("XETRA"));
        assertTrue(msg.contains("Tag der Arbeit"));
        assertTrue(msg.contains("PAR"));
        assertTrue(msg.contains("Fête du Travail"));
    }

    @Test
    void sendDailyReport_nyseAndInternational_listsAllExchanges() {
        MarketHolidayResponse.MarketHoliday nyseHoliday = new MarketHolidayResponse.MarketHoliday();
        nyseHoliday.setEventName("Christmas Day");
        nyseHoliday.setTradingHour("");
        when(marketStatusService.getTodayHoliday()).thenReturn(Optional.of(nyseHoliday));
        when(marketStatusService.getTodayInternationalHolidays())
                .thenReturn(Map.of(Exchange.XETRA, "Erster Weihnachtstag"));

        notifier.sendDailyReport();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String msg = messageCaptor.getValue();
        assertTrue(msg.contains("🇺🇸 NYSE"));
        assertTrue(msg.contains("Christmas Day"));
        assertTrue(msg.contains("XETRA"));
        assertTrue(msg.contains("Erster Weihnachtstag"));
    }

    @Test
    void sendDailyReport_nullTradingHour_treatsAsFullClose() {
        MarketHolidayResponse.MarketHoliday holiday = new MarketHolidayResponse.MarketHoliday();
        holiday.setEventName("Independence Day");
        holiday.setTradingHour(null);
        when(marketStatusService.getTodayHoliday()).thenReturn(Optional.of(holiday));
        when(marketStatusService.getTodayInternationalHolidays()).thenReturn(Map.of());

        notifier.sendDailyReport();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(messageCaptor.capture());
        String msg = messageCaptor.getValue();
        assertTrue(msg.contains("Independence Day"));
        assertTrue(!msg.contains("early close"));
    }
}
