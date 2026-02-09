package org.tradelite.client.finviz;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.tradelite.client.finviz.dto.IndustryPerformance;

@Slf4j
@Component
public class FinvizClient {

    private static final String FINVIZ_GROUPS_URL =
            "https://finviz.com/groups.ashx?g=industry&v=140&o=name";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public List<IndustryPerformance> fetchIndustryPerformance() throws IOException {
        log.info("Fetching industry performance from FinViz");

        Document doc =
                Jsoup.connect(FINVIZ_GROUPS_URL).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();

        Elements rows = doc.select("table.styled-table-new tbody tr");

        List<IndustryPerformance> performances = new ArrayList<>();
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 11) {
                IndustryPerformance performance = parseRow(cells);
                if (performance != null) {
                    performances.add(performance);
                }
            }
        }

        log.info("Fetched {} industry performances", performances.size());
        return performances;
    }

    IndustryPerformance parseRow(Elements cells) {
        try {
            String name = cells.get(1).select("a").text();
            BigDecimal perfWeek = parsePercentage(cells.get(2));
            BigDecimal perfMonth = parsePercentage(cells.get(3));
            BigDecimal perfQuarter = parsePercentage(cells.get(4));
            BigDecimal perfHalf = parsePercentage(cells.get(5));
            BigDecimal perfYear = parsePercentage(cells.get(6));
            BigDecimal perfYtd = parsePercentage(cells.get(7));
            BigDecimal change = parsePercentage(cells.get(10));

            return new IndustryPerformance(
                    name, perfWeek, perfMonth, perfQuarter, perfHalf, perfYear, perfYtd, change);
        } catch (Exception e) {
            log.warn("Failed to parse row: {}", e.getMessage());
            return null;
        }
    }

    BigDecimal parsePercentage(Element cell) {
        String text = cell.select("span").text();
        if (text.isEmpty()) {
            text = cell.text();
        }
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        String cleaned = text.replace("%", "").replace(",", "").trim();
        if (cleaned.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(cleaned);
    }
}
