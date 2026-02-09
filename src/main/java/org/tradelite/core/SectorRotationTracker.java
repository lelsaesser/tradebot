package org.tradelite.core;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.finviz.FinvizClient;
import org.tradelite.client.finviz.dto.IndustryPerformance;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.core.SectorPerformancePersistence.PerformancePeriod;

@Slf4j
@Component
@RequiredArgsConstructor
public class SectorRotationTracker {

    private static final String DATE_PATTERN = "%d. %s: %+.2f%%%n";

    private final FinvizClient finvizClient;
    private final SectorPerformancePersistence persistence;
    private final TelegramClient telegramClient;

    public void fetchAndStoreDailyPerformance() {
        try {
            List<IndustryPerformance> performances = finvizClient.fetchIndustryPerformance();
            if (performances.isEmpty()) {
                log.warn("No industry performances fetched from FinViz");
                return;
            }

            SectorPerformanceSnapshot snapshot =
                    new SectorPerformanceSnapshot(LocalDate.now(), performances);
            persistence.saveSnapshot(snapshot);

            sendDailySummary();
        } catch (IOException e) {
            log.error("Failed to fetch sector performance: {}", e.getMessage());
        }
    }

    void sendDailySummary() {
        StringBuilder report = new StringBuilder();
        report.append("📊 *Daily Sector Rotation Report*\n\n");

        report.append("*🔥 Top 5 Daily Performers:*\n");
        List<IndustryPerformance> topDaily =
                persistence.getTopPerformers(5, PerformancePeriod.DAILY);
        for (int i = 0; i < topDaily.size(); i++) {
            IndustryPerformance p = topDaily.get(i);
            report.append(
                    String.format(DATE_PATTERN, i + 1, p.name(), p.change().doubleValue()));
        }

        report.append("\n*❄️ Bottom 5 Daily Performers:*\n");
        List<IndustryPerformance> bottomDaily =
                persistence.getBottomPerformers(5, PerformancePeriod.DAILY);
        for (int i = 0; i < bottomDaily.size(); i++) {
            IndustryPerformance p = bottomDaily.get(i);
            report.append(
                    String.format(DATE_PATTERN, i + 1, p.name(), p.change().doubleValue()));
        }

        report.append("\n*📈 Top 5 Weekly Performers:*\n");
        List<IndustryPerformance> topWeekly =
                persistence.getTopPerformers(5, PerformancePeriod.WEEKLY);
        for (int i = 0; i < topWeekly.size(); i++) {
            IndustryPerformance p = topWeekly.get(i);
            report.append(
                    String.format(
                            DATE_PATTERN, i + 1, p.name(), p.perfWeek().doubleValue()));
        }

        report.append("\n*📉 Bottom 5 Weekly Performers:*\n");
        List<IndustryPerformance> bottomWeekly =
                persistence.getBottomPerformers(5, PerformancePeriod.WEEKLY);
        for (int i = 0; i < bottomWeekly.size(); i++) {
            IndustryPerformance p = bottomWeekly.get(i);
            report.append(
                    String.format(
                            DATE_PATTERN, i + 1, p.name(), p.perfWeek().doubleValue()));
        }

        telegramClient.sendMessage(report.toString());
    }

    public String generateSectorReport() {
        StringBuilder report = new StringBuilder();
        report.append("📊 *Sector Performance Overview*\n\n");

        report.append("*🔥 Top 10 Daily Performers:*\n");
        List<IndustryPerformance> topDaily =
                persistence.getTopPerformers(10, PerformancePeriod.DAILY);
        for (int i = 0; i < topDaily.size(); i++) {
            IndustryPerformance p = topDaily.get(i);
            report.append(
                    String.format(DATE_PATTERN, i + 1, p.name(), p.change().doubleValue()));
        }

        report.append("\n*❄️ Bottom 10 Daily Performers:*\n");
        List<IndustryPerformance> bottomDaily =
                persistence.getBottomPerformers(10, PerformancePeriod.DAILY);
        for (int i = 0; i < bottomDaily.size(); i++) {
            IndustryPerformance p = bottomDaily.get(i);
            report.append(
                    String.format(DATE_PATTERN, i + 1, p.name(), p.change().doubleValue()));
        }

        return report.toString();
    }
}
