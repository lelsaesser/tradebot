package org.tradelite.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.Exchange;
import org.tradelite.service.MarketStatusService;

/**
 * Sends a consolidated Telegram message listing every supported exchange that is closed today
 * (NYSE via Finnhub, international exchanges via Enrico). Invoked by {@code
 * Scheduler.dailyMarketHolidayNotification}; extracted from Scheduler so the scheduler stays a thin
 * cron-definition layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketHolidayNotifier {

    private final MarketStatusService marketStatusService;
    private final TelegramGateway telegramClient;

    public void sendDailyReport() {
        List<String> lines = new ArrayList<>();

        Optional<MarketHolidayResponse.MarketHoliday> nyse = marketStatusService.getTodayHoliday();
        if (nyse.isPresent()) {
            MarketHolidayResponse.MarketHoliday h = nyse.get();
            String tradingHour = h.getTradingHour();
            if (tradingHour == null || tradingHour.isEmpty()) {
                lines.add("🇺🇸 NYSE — " + h.getEventName());
            } else {
                String closeTime = tradingHour.split("-")[1];
                lines.add(
                        "🇺🇸 NYSE — " + h.getEventName() + " (early close " + closeTime + " ET)");
            }
        }

        Map<Exchange, String> internationalHolidays =
                marketStatusService.getTodayInternationalHolidays();
        for (Map.Entry<Exchange, String> entry : internationalHolidays.entrySet()) {
            lines.add(flagFor(entry.getKey()) + " " + entry.getKey() + " — " + entry.getValue());
        }

        if (lines.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder("*Markets closed today*");
        for (String line : lines) {
            sb.append("\n").append(line);
        }
        telegramClient.sendMessage(sb.toString());
    }

    private static String flagFor(Exchange exchange) {
        return switch (exchange) {
            case XETRA -> "🇩🇪";
            case KRX -> "🇰🇷";
            case JPX -> "🇯🇵";
            case STO -> "🇸🇪";
            case PAR -> "🇫🇷";
        };
    }
}
