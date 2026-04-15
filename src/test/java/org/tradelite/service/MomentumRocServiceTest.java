package org.tradelite.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.core.MomentumRocSignal;
import org.tradelite.core.MomentumRocSignal.SignalType;
import org.tradelite.quant.StatisticsUtil;
import org.tradelite.repository.MomentumRocRepository;
import org.tradelite.service.model.DailyPrice;
import org.tradelite.service.model.MomentumRocData;

@ExtendWith(MockitoExtension.class)
class MomentumRocServiceTest {

    @Mock private DailyPriceProvider dailyPriceProvider;
    @Mock private MomentumRocRepository momentumRocRepository;

    private MomentumRocService service;

    @BeforeEach
    void setUp() {
        service = new MomentumRocService(dailyPriceProvider, momentumRocRepository);
    }

    @Test
    void calculateRoc_insufficientData_returnsEmpty() {
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35))
                .thenReturn(List.of(new DailyPrice(LocalDate.now(), 100.0)));

        Optional<MomentumRocService.RocResult> result = service.calculateRoc("XLK");

        assertTrue(result.isEmpty());
    }

    @Test
    void calculateRoc_sufficientData_returnsRocValues() {
        List<DailyPrice> prices = createPriceHistory(25, 100.0, 0.5);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocService.RocResult> result = service.calculateRoc("XLK");

        assertTrue(result.isPresent());
        assertTrue(result.get().isComplete());
        assertEquals(25, result.get().dataPoints());
    }

    @Test
    void calculateRocValue_correctFormula() {
        // Create prices where current = 110, 10 days ago = 100
        // ROC10 = ((110 - 100) / 100) * 100 = 10%
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(24);

        for (int i = 0; i < 25; i++) {
            double price = 100.0 + (i * 0.4); // Gradual increase
            prices.add(new DailyPrice(startDate.plusDays(i), price));
        }

        double roc10 = StatisticsUtil.calculateRocValue(prices, 10);

        // (109.6 - 105.6) / 105.6 * 100 = 3.79%
        assertTrue(roc10 > 0);
    }

    @Test
    void calculateRocValue_negativeRoc() {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(24);

        for (int i = 0; i < 25; i++) {
            double price = 120.0 - (i * 0.4); // Gradual decrease
            prices.add(new DailyPrice(startDate.plusDays(i), price));
        }

        double roc10 = StatisticsUtil.calculateRocValue(prices, 10);

        assertTrue(roc10 < 0);
    }

    @Test
    void calculateRocValue_zeroBasePriceReturnsZero() {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(24);

        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(startDate.plusDays(i), i == 14 ? 0.0 : 100.0));
        }

        double roc10 = StatisticsUtil.calculateRocValue(prices, 10);

        assertEquals(0.0, roc10);
    }

    @Test
    void calculateRocValue_insufficientDataReturnsZero() {
        List<DailyPrice> prices = List.of(new DailyPrice(LocalDate.now(), 100.0));

        double roc10 = StatisticsUtil.calculateRocValue(prices, 10);

        assertEquals(0.0, roc10);
    }

    @Test
    void detectMomentumShift_firstCalculation_noSignal() {
        List<DailyPrice> prices = createPriceHistory(25, 100.0, 0.5);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.empty());

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLK", "Technology");

        assertTrue(result.isEmpty());

        // Verify state was saved for next time
        ArgumentCaptor<MomentumRocData> captor = ArgumentCaptor.forClass(MomentumRocData.class);
        verify(momentumRocRepository).save(eq("XLK"), captor.capture());
        assertTrue(captor.getValue().isInitialized());
    }

    @Test
    void detectMomentumShift_crossoverPositive_returnsSignal() {
        // Previous state: ROC10 was negative
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(-5.0);
        previousData.setPreviousRoc20(-3.0);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.of(previousData));

        // Current prices show positive ROC
        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 100.0, 1.0); // Uptrend
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLK", "Technology");

        assertTrue(result.isPresent());
        assertEquals(SignalType.MOMENTUM_TURNING_POSITIVE, result.get().signalType());
        assertEquals("XLK", result.get().symbol());
        assertEquals("Technology", result.get().displayName());
    }

    @Test
    void detectMomentumShift_crossoverNegative_returnsSignal() {
        // Previous state: ROC10 was positive
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(5.0);
        previousData.setPreviousRoc20(3.0);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.of(previousData));

        // Current prices show negative ROC
        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 120.0, -1.0); // Downtrend
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLK", "Technology");

        assertTrue(result.isPresent());
        assertEquals(SignalType.MOMENTUM_TURNING_NEGATIVE, result.get().signalType());
    }

    @Test
    void detectMomentumShift_noCrossover_noSignal() {
        // Previous state: ROC10 was positive
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(3.0);
        previousData.setPreviousRoc20(2.0);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.of(previousData));

        // Current ROC still positive (no crossover)
        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 100.0, 1.0);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLK", "Technology");

        assertTrue(result.isEmpty());
    }

    @Test
    void detectMomentumShift_insufficientData_returnsEmpty() {
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35))
                .thenReturn(List.of(new DailyPrice(LocalDate.now(), 100.0)));

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLK", "Technology");

        assertTrue(result.isEmpty());
        verify(momentumRocRepository, never()).save(any(), any());
    }

    @Test
    void detectMomentumShift_savesPreviousState() {
        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 100.0, 0.5);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.empty());

        service.detectMomentumShift("XLK", "Technology");

        ArgumentCaptor<MomentumRocData> captor = ArgumentCaptor.forClass(MomentumRocData.class);
        verify(momentumRocRepository).save(eq("XLK"), captor.capture());

        MomentumRocData savedData = captor.getValue();
        assertTrue(savedData.isInitialized());
        // Values should be set (exact values depend on price data)
        assertNotEquals(0.0, savedData.getPreviousRoc10());
    }

    @Test
    void detectMomentumShift_updatesExistingStateOnCrossover() {
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(-5.0);
        previousData.setPreviousRoc20(-3.0);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.of(previousData));

        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 100.0, 1.0);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        service.detectMomentumShift("XLK", "Technology");

        ArgumentCaptor<MomentumRocData> captor = ArgumentCaptor.forClass(MomentumRocData.class);
        verify(momentumRocRepository).save(eq("XLK"), captor.capture());

        MomentumRocData savedData = captor.getValue();
        assertTrue(savedData.isInitialized());
        assertTrue(savedData.getPreviousRoc10() > 0);
    }

    @Test
    void calculateRocValue_exactBoundary_periodEqualsListSize() {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(10);
        for (int i = 0; i <= 10; i++) {
            prices.add(new DailyPrice(startDate.plusDays(i), 100.0 + i));
        }

        double roc10 = StatisticsUtil.calculateRocValue(prices, 10);

        assertEquals(10.0, roc10, 0.01);
    }

    @Test
    void calculateRoc_exactMinimumDataPoints_returnsResult() {
        List<DailyPrice> prices = createPriceHistory(21, 100.0, 0.5);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocService.RocResult> result = service.calculateRoc("XLK");

        assertTrue(result.isPresent());
        assertTrue(result.get().isComplete());
        assertEquals(21, result.get().dataPoints());
    }

    @Test
    void calculateRoc_belowMinimumDataPoints_returnsEmpty() {
        List<DailyPrice> prices = createPriceHistory(20, 100.0, 0.5);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocService.RocResult> result = service.calculateRoc("XLK");

        assertTrue(result.isEmpty());
    }

    @Test
    void rocResult_isComplete_trueForSufficientData() {
        MomentumRocService.RocResult result = new MomentumRocService.RocResult(5.0, 3.0, 25, true);

        assertTrue(result.isComplete());
        assertEquals(5.0, result.roc10());
        assertEquals(3.0, result.roc20());
        assertEquals(25, result.dataPoints());
    }

    @Test
    void rocResult_isComplete_falseForInsufficientData() {
        MomentumRocService.RocResult result = new MomentumRocService.RocResult(5.0, 3.0, 15, false);

        assertFalse(result.isComplete());
    }

    @Test
    void detectMomentumShift_negativeToNegative_noSignal() {
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(-5.0);
        previousData.setPreviousRoc20(-3.0);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.of(previousData));

        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 120.0, -0.5);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLK", "Technology");

        assertTrue(result.isEmpty());
    }

    @Test
    void detectMomentumShift_positiveToPositive_noSignal() {
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(5.0);
        previousData.setPreviousRoc20(3.0);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.of(previousData));

        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 100.0, 0.5);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLK", "Technology");

        assertTrue(result.isEmpty());
    }

    @Test
    void detectMomentumShift_belowDeadZoneToAboveDeadZone_signalGenerated() {
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(-0.5); // Below dead zone (-0.25)
        previousData.setPreviousRoc20(-0.5);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.of(previousData));

        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 100.0, 1.0);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLK", "Technology");

        assertTrue(result.isPresent());
        assertEquals(SignalType.MOMENTUM_TURNING_POSITIVE, result.get().signalType());
    }

    @Test
    void detectMomentumShift_aboveDeadZoneToBelowDeadZone_signalGenerated() {
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(0.5); // Above dead zone (+0.25)
        previousData.setPreviousRoc20(0.5);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.of(previousData));

        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 120.0, -1.0);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLK", "Technology");

        assertTrue(result.isPresent());
        assertEquals(SignalType.MOMENTUM_TURNING_NEGATIVE, result.get().signalType());
    }

    @Test
    void detectMomentumShift_withinDeadZone_noSignal() {
        // Previous ROC10 = -0.1 (inside dead zone), current would be slightly positive
        // Both are within ±0.25 dead zone → no signal
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(-0.1);
        previousData.setPreviousRoc20(-2.0);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLV")).thenReturn(Optional.of(previousData));

        // Create prices that produce a small positive ROC10 (within dead zone)
        List<DailyPrice> prices = createPriceHistory(25, 100.0, 0.01);
        when(dailyPriceProvider.findDailyClosingPrices("XLV", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLV", "Health Care");

        assertTrue(
                result.isEmpty(),
                "Should not trigger signal when previous ROC is within dead zone");
    }

    @Test
    void detectMomentumShift_previousInsideDeadZoneCurrentOutside_noSignal() {
        // Previous ROC10 = +0.1 (inside dead zone), current = -1.0 (outside dead zone)
        // No signal because previous was not meaningfully above the dead zone
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(0.1);
        previousData.setPreviousRoc20(0.1);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLV")).thenReturn(Optional.of(previousData));

        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 120.0, -1.0);
        when(dailyPriceProvider.findDailyClosingPrices("XLV", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLV", "Health Care");

        assertTrue(result.isEmpty(), "Should not trigger when previous ROC is inside dead zone");
    }

    @Test
    void detectMomentumShift_deadZoneBoundary_noSignal() {
        // Previous ROC10 = exactly at dead zone boundary (-0.25)
        // Dead zone check is strict inequality: previousRoc10 < -0.25 is false for -0.25
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(-MomentumRocService.ROC_DEAD_ZONE);
        previousData.setPreviousRoc20(-2.0);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLV")).thenReturn(Optional.of(previousData));

        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 100.0, 1.0);
        when(dailyPriceProvider.findDailyClosingPrices("XLV", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLV", "Health Care");

        assertTrue(result.isEmpty(), "Exact dead zone boundary should not trigger signal");
    }

    @Test
    void detectMomentumShift_signalContainsPreviousValues() {
        MomentumRocData previousData = new MomentumRocData();
        previousData.setPreviousRoc10(-5.5);
        previousData.setPreviousRoc20(-3.3);
        previousData.setInitialized(true);
        when(momentumRocRepository.findBySymbol("XLK")).thenReturn(Optional.of(previousData));

        List<DailyPrice> prices = createPriceHistoryWithTrend(25, 100.0, 1.0);
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<MomentumRocSignal> result = service.detectMomentumShift("XLK", "Technology");

        assertTrue(result.isPresent());
        assertEquals(-5.5, result.get().previousRoc10(), 0.01);
        assertEquals(-3.3, result.get().previousRoc20(), 0.01);
    }

    private List<DailyPrice> createPriceHistory(int days, double startPrice, double dailyChange) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(days - 1);

        for (int i = 0; i < days; i++) {
            double price = startPrice + (i * dailyChange);
            prices.add(new DailyPrice(startDate.plusDays(i), price));
        }
        return prices;
    }

    private List<DailyPrice> createPriceHistoryWithTrend(
            int days, double startPrice, double dailyChange) {
        return createPriceHistory(days, startPrice, dailyChange);
    }
}
