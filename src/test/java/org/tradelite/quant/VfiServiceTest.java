package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.repository.OhlcvRepository;

@ExtendWith(MockitoExtension.class)
class VfiServiceTest {

    @Mock private OhlcvRepository ohlcvRepository;

    private VfiService service;

    @BeforeEach
    void setUp() {
        service = new VfiService(ohlcvRepository);
    }

    @Test
    void analyze_returnsEmptyWhenInsufficientData() {
        List<OhlcvRecord> records = generateRecords(100, 100.0, 0.5, 1_000_000L);
        when(ohlcvRepository.findBySymbol("AAPL", 200)).thenReturn(records);

        Optional<VfiAnalysis> result = service.analyze("AAPL", "Apple");

        assertThat(result).isEmpty();
    }

    @Test
    void analyze_returnsAnalysisWithSufficientData() {
        List<OhlcvRecord> records = generateRecords(136, 100.0, 0.5, 1_000_000L);
        when(ohlcvRepository.findBySymbol("AAPL", 200)).thenReturn(records);

        Optional<VfiAnalysis> result = service.analyze("AAPL", "Apple");

        assertThat(result).isPresent();
        VfiAnalysis analysis = result.get();
        assertThat(analysis.symbol()).isEqualTo("AAPL");
        assertThat(analysis.displayName()).isEqualTo("Apple");
    }

    @Test
    void calculateVfi_constantPrices_returnsZeroVfi() {
        List<OhlcvRecord> records = generateConstantRecords(136, 100.0, 1_000_000L);

        VfiAnalysis result = service.calculateVfi("TEST", "Test", records);

        assertThat(result.vfiValue()).isCloseTo(0.0, within(0.01));
        assertThat(result.signalLineValue()).isCloseTo(0.0, within(0.01));
        assertThat(result.isVfiPositive()).isFalse();
    }

    @Test
    void calculateVfi_risingPrices_returnsPositiveVfi() {
        List<OhlcvRecord> records = generateTrendingRecords(136, 100.0, 0.5, 1_000_000L);

        VfiAnalysis result = service.calculateVfi("TEST", "Test", records);

        assertThat(result.vfiValue()).isGreaterThan(0);
    }

    @Test
    void calculateVfi_fallingPrices_returnsNegativeVfi() {
        List<OhlcvRecord> records = generateTrendingRecords(136, 200.0, -0.5, 1_000_000L);

        VfiAnalysis result = service.calculateVfi("TEST", "Test", records);

        assertThat(result.vfiValue()).isLessThan(0);
    }

    @Test
    void calculateVfi_volumeCapping_respectsVmax() {
        // Generate records with one extreme volume spike
        List<OhlcvRecord> records = generateTrendingRecords(136, 100.0, 0.5, 1_000_000L);

        // Create a copy with a massive volume spike at the end
        List<OhlcvRecord> spikedRecords = new ArrayList<>(records);
        OhlcvRecord lastRecord = spikedRecords.getLast();
        OhlcvRecord spikedRecord =
                new OhlcvRecord(
                        lastRecord.symbol(),
                        lastRecord.date(),
                        lastRecord.open(),
                        lastRecord.high(),
                        lastRecord.low(),
                        lastRecord.close(),
                        100_000_000_000L); // 100x normal
        spikedRecords.set(spikedRecords.size() - 1, spikedRecord);

        VfiAnalysis normalResult = service.calculateVfi("TEST", "Test", records);
        VfiAnalysis spikedResult = service.calculateVfi("TEST", "Test", spikedRecords);

        // The spike should be capped at vmax (vave * 2.5), so the difference is bounded
        // Without capping, the spike would dominate the VFI entirely
        assertThat(Math.abs(spikedResult.vfiValue() - normalResult.vfiValue())).isLessThan(10.0);
    }

    @Test
    void calculateVfi_zeroVolume_handlesGracefully() {
        List<OhlcvRecord> records = generateRecords(136, 100.0, 0.5, 0L);

        VfiAnalysis result = service.calculateVfi("TEST", "Test", records);

        assertThat(result.vfiValue()).isCloseTo(0.0, within(0.01));
        assertThat(result.signalLineValue()).isCloseTo(0.0, within(0.01));
    }

    @Test
    void calculateVfi_isVfiPositive_requiresBothPositive() {
        // Rising prices should give positive VFI and positive signal
        List<OhlcvRecord> records = generateTrendingRecords(136, 100.0, 0.5, 1_000_000L);

        VfiAnalysis result = service.calculateVfi("TEST", "Test", records);

        if (result.vfiValue() > 0 && result.signalLineValue() > 0) {
            assertThat(result.isVfiPositive()).isTrue();
        } else {
            assertThat(result.isVfiPositive()).isFalse();
        }
    }

    private List<OhlcvRecord> generateRecords(
            int count, double basePrice, double volatility, long volume) {
        List<OhlcvRecord> records = new ArrayList<>();
        LocalDate date = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < count; i++) {
            double price = basePrice + volatility * Math.sin(i * 0.3);
            double high = price * 1.01;
            double low = price * 0.99;
            records.add(new OhlcvRecord("TEST", date.plusDays(i), price, high, low, price, volume));
        }
        return records;
    }

    private List<OhlcvRecord> generateConstantRecords(int count, double price, long volume) {
        List<OhlcvRecord> records = new ArrayList<>();
        LocalDate date = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < count; i++) {
            records.add(
                    new OhlcvRecord("TEST", date.plusDays(i), price, price, price, price, volume));
        }
        return records;
    }

    private List<OhlcvRecord> generateTrendingRecords(
            int count, double startPrice, double dailyChange, long volume) {
        List<OhlcvRecord> records = new ArrayList<>();
        LocalDate date = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < count; i++) {
            double price = startPrice + dailyChange * i;
            double high = price + Math.abs(dailyChange) * 0.5;
            double low = price - Math.abs(dailyChange) * 0.5;
            records.add(new OhlcvRecord("TEST", date.plusDays(i), price, high, low, price, volume));
        }
        return records;
    }
}
