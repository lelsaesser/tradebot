package org.tradelite.service.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelativeStrengthDataTest {

    private RelativeStrengthData rsData;

    @BeforeEach
    void setUp() {
        rsData = new RelativeStrengthData();
    }

    @Test
    void testAddRsValue() {
        LocalDate today = LocalDate.now();
        rsData.addRsValue(today, 1.25);

        List<Double> rsValues = rsData.getRsValues();
        assertThat(rsValues.size(), is(1));
        assertThat(rsValues.get(0), is(1.25));
    }

    @Test
    void testAddRsValue_updatesExistingDate() {
        LocalDate today = LocalDate.now();

        rsData.addRsValue(today, 1.25);
        rsData.addRsValue(today, 1.50); // Update same date

        List<Double> rsValues = rsData.getRsValues();
        assertThat(rsValues.size(), is(1));
        assertThat(rsValues.get(0), is(1.50)); // Should have updated value
    }

    @Test
    void testAddRsValue_multipleValues() {
        LocalDate today = LocalDate.now();

        rsData.addRsValue(today.minusDays(2), 1.10);
        rsData.addRsValue(today.minusDays(1), 1.20);
        rsData.addRsValue(today, 1.30);

        List<Double> rsValues = rsData.getRsValues();
        assertThat(rsValues.size(), is(3));
        // Should be sorted chronologically (oldest first)
        assertThat(rsValues.get(0), is(1.10));
        assertThat(rsValues.get(1), is(1.20));
        assertThat(rsValues.get(2), is(1.30));
    }

    @Test
    void testAddRsValue_limitTo200Values() {
        LocalDate baseDate = LocalDate.now().minusDays(250);

        // Add 210 values
        for (int i = 0; i < 210; i++) {
            rsData.addRsValue(baseDate.plusDays(i), 1.0 + (i * 0.01));
        }

        // Should be limited to 200
        assertThat(rsData.getRsHistory().size(), is(200));

        // Should keep the most recent 200 values
        List<Double> rsValues = rsData.getRsValues();
        // First value should be from day 10 (index 10)
        assertThat(rsValues.get(0), is(closeTo(1.10, 0.001)));
        // Last value should be from day 209
        assertThat(rsValues.get(199), is(closeTo(3.09, 0.001)));
    }

    @Test
    void testGetRsValues_returnsSortedChronologically() {
        LocalDate today = LocalDate.now();

        // Add in non-chronological order
        rsData.addRsValue(today, 1.30);
        rsData.addRsValue(today.minusDays(2), 1.10);
        rsData.addRsValue(today.minusDays(1), 1.20);

        List<Double> rsValues = rsData.getRsValues();

        // Should be sorted oldest to newest
        assertThat(rsValues.get(0), is(1.10));
        assertThat(rsValues.get(1), is(1.20));
        assertThat(rsValues.get(2), is(1.30));
    }

    @Test
    void testGetLatestRs() {
        LocalDate today = LocalDate.now();

        rsData.addRsValue(today.minusDays(2), 1.10);
        rsData.addRsValue(today.minusDays(1), 1.20);
        rsData.addRsValue(today, 1.30);

        assertThat(rsData.getLatestRs(), is(1.30));
    }

    @Test
    void testGetLatestRs_emptyHistory() {
        assertThat(rsData.getLatestRs(), is(0.0));
    }

    @Test
    void testGetLatestRs_singleValue() {
        rsData.addRsValue(LocalDate.now(), 1.50);

        assertThat(rsData.getLatestRs(), is(1.50));
    }

    @Test
    void testPreviousRsAndEma() {
        rsData.setPreviousRs(1.25);
        rsData.setPreviousEma(1.20);

        assertThat(rsData.getPreviousRs(), is(1.25));
        assertThat(rsData.getPreviousEma(), is(1.20));
    }

    @Test
    void testInitializedFlag() {
        assertThat(rsData.isInitialized(), is(false));

        rsData.setInitialized(true);
        assertThat(rsData.isInitialized(), is(true));
    }

    @Test
    void testGetRsValues_emptyHistory() {
        List<Double> rsValues = rsData.getRsValues();
        assertThat(rsValues, is(empty()));
    }

    @Test
    void testGetRsHistory() {
        LocalDate today = LocalDate.now();

        rsData.addRsValue(today.minusDays(1), 1.10);
        rsData.addRsValue(today, 1.20);

        List<DailyPrice> history = rsData.getRsHistory();
        assertThat(history.size(), is(2));
    }

    @Test
    void testSetRsHistory() {
        List<DailyPrice> newHistory = List.of(createDailyPrice(LocalDate.now(), 1.25));

        rsData.setRsHistory(new java.util.ArrayList<>(newHistory));

        assertThat(rsData.getRsHistory().size(), is(1));
        assertThat(rsData.getLatestRs(), is(1.25));
    }

    private DailyPrice createDailyPrice(LocalDate date, double price) {
        DailyPrice dailyPrice = new DailyPrice();
        dailyPrice.setDate(date);
        dailyPrice.setPrice(price);
        return dailyPrice;
    }
}
