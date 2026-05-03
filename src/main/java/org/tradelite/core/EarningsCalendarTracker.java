package org.tradelite.core;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.EarningsCalendarResponse;
import org.tradelite.client.finnhub.dto.EarningsCalendarResponse.EarningsEvent;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.service.FeatureToggleService;

@Slf4j
@Component
@RequiredArgsConstructor
public class EarningsCalendarTracker {

    private static final int LOOK_AHEAD_DAYS = 7;

    private final FinnhubClient finnhubClient;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;
    private final FeatureToggleService featureToggleService;

    public void checkAndAlert() {
        if (!featureToggleService.isEnabled(FeatureToggle.EARNINGS_CALENDAR_ALERT)) {
            return;
        }

        LocalDate today = LocalDate.now();
        String from = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String to = today.plusDays(LOOK_AHEAD_DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE);

        EarningsCalendarResponse response = finnhubClient.getEarningsCalendar(from, to);
        if (response == null || response.getEarningsCalendar() == null) {
            log.warn("No earnings calendar data received from Finnhub");
            return;
        }

        Set<String> trackedTickers =
                symbolRegistry.getStocks().stream()
                        .map(StockSymbol::getTicker)
                        .collect(Collectors.toSet());

        List<EarningsEvent> matchingEvents =
                response.getEarningsCalendar().stream()
                        .filter(e -> trackedTickers.contains(e.getSymbol()))
                        .toList();

        if (matchingEvents.isEmpty()) {
            log.info(
                    "No upcoming earnings for tracked stocks in the next {} days", LOOK_AHEAD_DAYS);
            return;
        }

        String message = buildAlertMessage(matchingEvents);
        telegramClient.sendMessage(message);
        log.info("Sent earnings calendar alert for {} stock(s)", matchingEvents.size());
    }

    String buildAlertMessage(List<EarningsEvent> events) {
        Map<LocalDate, List<EarningsEvent>> groupedByDate =
                events.stream()
                        .collect(
                                Collectors.groupingBy(
                                        e -> LocalDate.parse(e.getDate()),
                                        TreeMap::new,
                                        Collectors.toList()));

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCC5 *Earnings Calendar*\n");

        for (Map.Entry<LocalDate, List<EarningsEvent>> entry : groupedByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<EarningsEvent> dayEvents = entry.getValue();

            String dayName = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            String monthName = date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            sb.append(String.format("%n*%s, %s %d:*%n", dayName, monthName, date.getDayOfMonth()));

            for (EarningsEvent event : dayEvents) {
                String displayName = resolveDisplayName(event.getSymbol());
                sb.append(String.format("• %s (%s)%n", displayName, event.getSymbol()));
            }
        }

        return sb.toString().trim();
    }

    private String resolveDisplayName(String ticker) {
        return symbolRegistry.fromString(ticker).map(StockSymbol::getCompanyName).orElse(ticker);
    }
}
