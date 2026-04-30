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
        when(ohlcvRepository.findBySymbol("AAPL", 400)).thenReturn(records);

        Optional<VfiAnalysis> result = service.analyze("AAPL", "Apple");

        assertThat(result).isEmpty();
    }

    @Test
    void analyze_returnsAnalysisWithSufficientData() {
        List<OhlcvRecord> records = generateRecords(136, 100.0, 0.5, 1_000_000L);
        when(ohlcvRepository.findBySymbol("AAPL", 400)).thenReturn(records);

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

    @Test
    void calculateVfi_goldenData_matchesExpectedValues() {
        // Real AAPL OHLCV data (2025-09-29 to 2026-04-14, 136 trading days)
        // Expected VFI ~4.59, Signal ~5.12 (computed independently)
        List<OhlcvRecord> records = buildAaplGoldenData();

        VfiAnalysis result = service.calculateVfi("AAPL", "Apple", records);

        assertThat(result.vfiValue()).isCloseTo(4.59, within(1.0));
        assertThat(result.signalLineValue()).isCloseTo(5.12, within(1.0));
        assertThat(result.isVfiPositive()).isTrue();
    }

    @Test
    void calculateVfi_manyWindows_signalEmaStabilizes() {
        // With 280 records, numWindows = max(280 - 130, 6) = 150 windows
        // The signal EMA should stabilize and differ from the raw last VFI value
        // Use sinusoidal volatility so VFI values vary across windows
        List<OhlcvRecord> records = generateRecords(280, 100.0, 2.0, 1_000_000L);

        VfiAnalysis result = service.calculateVfi("TEST", "Test", records);

        // With many windows the signal EMA smooths the oscillating VFI series,
        // so signal and raw VFI should differ
        double difference = Math.abs(result.vfiValue() - result.signalLineValue());
        assertThat(difference).isGreaterThan(0.01);
    }

    private List<OhlcvRecord> buildAaplGoldenData() {
        // Real AAPL OHLCV data with full Twelve Data precision (136 trading days)
        double[][] data = {
            {254.56, 255.0, 253.0099945, 254.42999, 40127700},
            {254.86, 255.92, 253.11, 254.63, 37704300},
            {255.039993, 258.79001, 254.92999, 255.45, 48713900},
            {256.57999, 258.17999, 254.14999, 257.13, 42630200},
            {254.67, 259.23999, 253.95, 258.019989, 49155600},
            {257.98999, 259.070007, 255.050003, 256.69, 44664100},
            {256.81, 257.39999, 255.42999, 256.48001, 31955800},
            {256.51999, 258.51999, 256.10999, 258.059998, 36496900},
            {257.81, 258.0, 253.14, 254.039993, 38322000},
            {254.94, 256.38, 244.0, 245.27, 61999100},
            {249.38, 249.69, 245.56, 247.66, 38142900},
            {246.60001, 248.85001, 244.7, 247.77, 35478000},
            {249.49001, 251.82001, 247.47, 249.34, 33893600},
            {248.25, 249.039993, 245.13, 247.45, 39777000},
            {248.020004, 253.38, 247.27, 252.28999, 49147000},
            {255.89, 264.38, 255.63, 262.23999, 90483000},
            {261.88, 265.29001, 261.82999, 262.76999, 46695900},
            {262.64999, 262.85001, 255.42999, 258.45001, 45015300},
            {259.94, 260.62, 258.01001, 259.57999, 32754900},
            {261.19, 264.13, 259.17999, 262.82001, 38253700},
            {264.88, 269.12, 264.64999, 268.81, 44888200},
            {268.98999, 269.89001, 268.14999, 269.0, 41534800},
            {269.28, 271.41, 267.10999, 269.70001, 51086700},
            {271.98999, 274.14001, 268.48001, 271.39999, 69886500},
            {276.98999, 277.32001, 269.16, 270.37, 86167100},
            {270.42001, 270.85001, 266.25, 269.049988, 50194600},
            {268.32999, 271.48999, 267.62, 270.040009, 49274800},
            {268.60999, 271.70001, 266.92999, 270.14001, 43683100},
            {267.89001, 273.39999, 267.89001, 269.76999, 51204000},
            {269.79999, 272.29001, 266.76999, 268.47, 48227400},
            {268.95999, 273.73001, 267.45999, 269.42999, 41312400},
            {269.81, 275.91, 269.79999, 275.25, 46208300},
            {275.0, 275.73001, 271.70001, 273.47, 48398000},
            {274.10999, 276.70001, 272.089996, 272.95001, 49602800},
            {271.049988, 275.95999, 269.60001, 272.41, 47431300},
            {268.82001, 270.48999, 265.73001, 267.45999, 45018300},
            {269.98999, 270.70999, 265.32001, 267.44, 45677300},
            {265.53, 272.20999, 265.5, 268.56, 40424500},
            {270.82999, 275.42999, 265.92001, 266.25, 45823600},
            {265.95001, 273.32999, 265.67001, 271.48999, 59030800},
            {270.89999, 277.0, 270.89999, 275.92001, 65585800},
            {275.26999, 280.38, 275.25, 276.97, 46914200},
            {276.95999, 279.53, 276.63, 277.54999, 33431400},
            {277.26001, 279.0, 275.98999, 278.85001, 20135600},
            {278.01001, 283.42001, 276.14001, 283.10001, 46587700},
            {283.0, 287.39999, 282.63, 286.19, 53669500},
            {286.20001, 288.62, 283.29999, 284.14999, 43538700},
            {284.10001, 284.73001, 278.59, 280.70001, 43989100},
            {280.54001, 281.14001, 278.049988, 278.78, 47265800},
            {278.13, 279.67001, 276.14999, 277.89001, 38211800},
            {278.16, 280.029999, 276.92001, 277.17999, 32193300},
            {277.75, 279.75, 276.44, 278.78, 33038300},
            {279.10001, 279.59, 273.81, 278.029999, 33248000},
            {277.89999, 279.22, 276.82001, 278.28, 39532900},
            {280.14999, 280.14999, 272.84, 274.10999, 50409100},
            {272.82001, 275.5, 271.79001, 274.60999, 37648600},
            {275.01001, 276.16, 271.64001, 271.84, 50138700},
            {273.60999, 273.63, 266.95001, 272.19, 51630700},
            {272.14999, 274.60001, 269.89999, 273.67001, 144632000},
            {272.85999, 273.88, 270.51001, 270.97, 36571800},
            {270.84, 272.5, 269.56, 272.35999, 29642000},
            {272.34, 275.42999, 272.20001, 273.81, 17910600},
            {274.16, 275.37, 272.85999, 273.39999, 21521800},
            {272.69, 274.35999, 272.35001, 273.76001, 23715200},
            {272.81, 274.079987, 272.28, 273.079987, 22139600},
            {273.059998, 273.67999, 271.75, 271.85999, 27293600},
            {272.26001, 277.84, 269.0, 271.01001, 37838100},
            {270.64001, 271.51001, 266.14001, 267.26001, 45647200},
            {267.0, 267.54999, 262.12, 262.35999, 52352100},
            {263.20001, 263.67999, 259.81, 260.32999, 48309800},
            {257.019989, 259.29001, 255.7, 259.040009, 50419300},
            {259.079987, 260.20999, 256.22, 259.37, 39997000},
            {259.16, 261.29999, 256.79999, 260.25, 45263800},
            {258.72, 261.81, 258.39001, 261.049988, 45730800},
            {259.48999, 261.82001, 256.70999, 259.95999, 40019400},
            {260.64999, 261.040009, 257.049988, 258.20999, 39388600},
            {257.89999, 258.89999, 254.92999, 255.53, 72142800},
            {252.73, 254.78999, 243.42, 246.7, 80267500},
            {248.7, 251.56, 245.17999, 247.64999, 54641700},
            {249.2, 251.0, 248.14999, 248.35001, 39708300},
            {247.32001, 249.41, 244.67999, 248.039993, 41689000},
            {251.48, 256.56, 249.8, 255.41, 55969200},
            {259.17001, 261.95001, 258.20999, 258.26999, 49648300},
            {257.64999, 258.85999, 254.50999, 256.44, 41288000},
            {258.0, 259.64999, 254.41, 258.28, 67253000},
            {255.17, 261.89999, 252.17999, 259.48001, 92443400},
            {260.029999, 270.48999, 259.20999, 270.01001, 73913400},
            {269.20001, 271.88, 267.60999, 269.48001, 64394700},
            {272.29001, 278.95001, 272.29001, 276.48999, 90545700},
            {278.13, 279.5, 273.23001, 275.91, 52977400},
            {277.12, 280.91, 276.92999, 278.12, 50453400},
            {277.91, 278.20001, 271.70001, 274.62, 44623400},
            {274.89001, 275.37, 272.94, 273.67999, 34376900},
            {274.70001, 280.17999, 274.45001, 275.5, 51931300},
            {275.59, 275.72, 260.17999, 261.73001, 81077200},
            {262.01001, 262.23001, 255.45, 255.78, 56290700},
            {258.049988, 266.29001, 255.53999, 263.88, 58469100},
            {263.60001, 266.82001, 262.45001, 264.35001, 34203300},
            {262.60001, 264.48001, 260.049988, 260.57999, 30845300},
            {258.97, 264.75, 258.16, 264.57999, 42070500},
            {263.48999, 269.42999, 263.38, 266.17999, 37308200},
            {267.85999, 274.89001, 267.70999, 272.14001, 47014600},
            {271.78, 274.94, 271.049988, 274.23001, 33714300},
            {274.95001, 276.10999, 270.79999, 272.95001, 32345100},
            {272.81, 272.81, 262.89001, 264.17999, 72366500},
            {262.41, 266.53, 260.20001, 264.72, 41827900},
            {263.48001, 265.56, 260.13, 263.75, 38568900},
            {264.64999, 266.14999, 261.42001, 262.51999, 39803100},
            {260.79001, 261.56, 257.25, 260.29001, 49658600},
            {258.63, 258.76999, 254.37, 257.45999, 41120000},
            {255.69, 261.14999, 253.67999, 259.88, 38218500},
            {257.64999, 262.48001, 256.95001, 260.82999, 30590800},
            {261.089996, 262.13, 259.54999, 260.81, 26218900},
            {258.66, 258.95001, 254.17999, 255.75999, 40794000},
            {255.48, 256.32999, 249.52, 250.12, 36930000},
            {252.11, 253.89, 249.88, 252.82001, 32074200},
            {252.96001, 255.13, 252.17999, 254.23, 32361600},
            {252.63, 254.94, 249.0, 249.94, 35757900},
            {249.39999, 251.83, 247.3, 248.96001, 34864100},
            {247.98, 249.2, 246.0, 247.99001, 88331100},
            {253.97, 254.60001, 250.28, 251.49001, 40546100},
            {250.35001, 254.83, 249.55, 251.64, 45152300},
            {254.10001, 255.0, 251.60001, 252.62, 28476700},
            {252.12, 257.0, 250.77, 252.89, 41796700},
            {253.89999, 255.49001, 248.070007, 248.8, 47900000},
            {250.070007, 250.87, 245.50999, 246.63, 39446200},
            {247.91, 255.48, 247.10001, 253.78999, 49598100},
            {254.080002, 256.17999, 253.33, 255.63, 40059400},
            {254.2, 256.13, 250.64999, 255.92, 31289400},
            {256.51001, 262.16, 256.45999, 258.85999, 29329900},
            {256.16, 256.20001, 245.7, 253.5, 62148000},
            {258.45001, 259.75, 256.53, 258.89999, 41032800},
            {259.0, 261.12, 256.070007, 260.48999, 28121600},
            {259.98001, 262.19, 259.019989, 260.48001, 31291500},
            {259.85999, 260.17999, 256.66, 259.20001, 35080398},
            {259.355, 261.925, 257.27, 257.27, 570154},
        };

        List<OhlcvRecord> records = new ArrayList<>(data.length);
        LocalDate date = LocalDate.of(2025, 9, 29);
        for (double[] row : data) {
            records.add(
                    new OhlcvRecord("AAPL", date, row[0], row[1], row[2], row[3], (long) row[4]));
            date = date.plusDays(1);
            // Skip weekends for realistic dates (not critical for VFI calc)
            if (date.getDayOfWeek().getValue() == 6) date = date.plusDays(2);
        }
        return records;
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
