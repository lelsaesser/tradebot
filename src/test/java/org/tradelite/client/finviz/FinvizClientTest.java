package org.tradelite.client.finviz;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tradelite.client.finviz.dto.IndustryPerformance;

class FinvizClientTest {

    private FinvizClient finvizClient;

    @BeforeEach
    void setUp() {
        finvizClient = new FinvizClient();
    }

    @Test
    void parseRow_shouldParseValidRow() {
        String html =
                """
                <html><body><table>
                    <tr>
                        <td>1</td>
                        <td><a href="test">Technology</a></td>
                        <td><span class="color-text is-positive">5.25%</span></td>
                        <td><span class="color-text is-positive">10.50%</span></td>
                        <td><span class="color-text is-positive">15.75%</span></td>
                        <td><span class="color-text is-negative">-2.30%</span></td>
                        <td><span class="color-text is-positive">25.00%</span></td>
                        <td><span class="color-text is-positive">8.50%</span></td>
                        <td>100M</td>
                        <td>1.5</td>
                        <td><span class="color-text is-positive">2.75%</span></td>
                        <td>50M</td>
                    </tr>
                </table></body></html>
                """;

        Document doc = Jsoup.parse(html);
        Elements cells = doc.select("tr td");

        IndustryPerformance result = finvizClient.parseRow(cells);

        assertThat(result, is(notNullValue()));
        assertThat(result.name(), is("Technology"));
        assertThat(result.perfWeek(), is(new BigDecimal("5.25")));
        assertThat(result.perfMonth(), is(new BigDecimal("10.50")));
        assertThat(result.perfQuarter(), is(new BigDecimal("15.75")));
        assertThat(result.perfHalf(), is(new BigDecimal("-2.30")));
        assertThat(result.perfYear(), is(new BigDecimal("25.00")));
        assertThat(result.perfYtd(), is(new BigDecimal("8.50")));
        assertThat(result.change(), is(new BigDecimal("2.75")));
    }

    @Test
    void parseRow_shouldHandleNegativeValues() {
        String html =
                """
                <html><body><table>
                    <tr>
                        <td>1</td>
                        <td><a href="test">Healthcare</a></td>
                        <td><span class="color-text is-negative">-3.25%</span></td>
                        <td><span class="color-text is-negative">-7.50%</span></td>
                        <td><span class="color-text is-negative">-12.00%</span></td>
                        <td><span class="color-text is-negative">-15.25%</span></td>
                        <td><span class="color-text is-negative">-20.00%</span></td>
                        <td><span class="color-text is-negative">-5.50%</span></td>
                        <td>50M</td>
                        <td>0.8</td>
                        <td><span class="color-text is-negative">-1.25%</span></td>
                        <td>25M</td>
                    </tr>
                </table></body></html>
                """;

        Document doc = Jsoup.parse(html);
        Elements cells = doc.select("tr td");

        IndustryPerformance result = finvizClient.parseRow(cells);

        assertThat(result, is(notNullValue()));
        assertThat(result.name(), is("Healthcare"));
        assertThat(result.perfWeek(), is(new BigDecimal("-3.25")));
        assertThat(result.change(), is(new BigDecimal("-1.25")));
    }

    @Test
    void parseRow_shouldReturnNullForInvalidRow() {
        String html =
                """
                <html><body><table>
                    <tr>
                        <td>Invalid</td>
                        <td><a href="test">Test</a></td>
                        <td><span>Not a number</span></td>
                    </tr>
                </table></body></html>
                """;

        Document doc = Jsoup.parse(html);
        Elements cells = doc.select("tr td");

        IndustryPerformance result = finvizClient.parseRow(cells);

        assertThat(result, is(nullValue()));
    }

    @Test
    void parsePercentage_shouldParsePositivePercentage() {
        String html =
                "<html><body><table><tr><td><span class=\"color-text is-positive\">5.25%</span></td></tr></table></body></html>";
        Document doc = Jsoup.parse(html);

        BigDecimal result =
                finvizClient.parsePercentage(Objects.requireNonNull(doc.select("td").first()));

        assertThat(result, is(new BigDecimal("5.25")));
    }

    @Test
    void parsePercentage_shouldParseNegativePercentage() {
        String html =
                "<html><body><table><tr><td><span class=\"color-text is-negative\">-3.75%</span></td></tr></table></body></html>";
        Document doc = Jsoup.parse(html);

        BigDecimal result =
                finvizClient.parsePercentage(Objects.requireNonNull(doc.select("td").first()));

        assertThat(result, is(new BigDecimal("-3.75")));
    }

    @Test
    void parsePercentage_shouldReturnZeroForEmptyCell() {
        String html = "<html><body><table><tr><td><span></span></td></tr></table></body></html>";
        Document doc = Jsoup.parse(html);

        BigDecimal result =
                finvizClient.parsePercentage(Objects.requireNonNull(doc.select("td").first()));

        assertThat(result, is(BigDecimal.ZERO));
    }

    @Test
    void parsePercentage_shouldHandleTextWithoutSpan() {
        String html = "<html><body><table><tr><td>7.50%</td></tr></table></body></html>";
        Document doc = Jsoup.parse(html);

        BigDecimal result =
                finvizClient.parsePercentage(Objects.requireNonNull(doc.select("td").first()));

        assertThat(result, is(new BigDecimal("7.50")));
    }

    @Test
    void parsePercentage_shouldHandleCommasInNumbers() {
        String html =
                "<html><body><table><tr><td><span>1,234.56%</span></td></tr></table></body></html>";
        Document doc = Jsoup.parse(html);

        BigDecimal result =
                finvizClient.parsePercentage(Objects.requireNonNull(doc.select("td").first()));

        assertThat(result, is(new BigDecimal("1234.56")));
    }

    @Test
    void parsePercentage_shouldReturnZeroForEmptyText() {
        String html = "<html><body><table><tr><td></td></tr></table></body></html>";
        Document doc = Jsoup.parse(html);

        BigDecimal result =
                finvizClient.parsePercentage(Objects.requireNonNull(doc.select("td").first()));

        assertThat(result, is(BigDecimal.ZERO));
    }
}
