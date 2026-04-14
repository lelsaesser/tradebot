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

    private List<OhlcvRecord> buildAaplGoldenData() {
        // open, high, low, close, volume — 136 real AAPL trading days
        double[][] data = {
            {254.56, 255.0, 253.01, 254.43, 40127700},
            {254.86, 255.92, 253.11, 254.63, 37704300},
            {255.04, 258.79, 254.93, 255.45, 48713900},
            {256.58, 258.18, 254.15, 257.13, 42630200},
            {254.67, 259.24, 253.95, 258.02, 49155600},
            {257.99, 259.07, 255.05, 256.69, 44664100},
            {256.81, 257.40, 255.43, 256.48, 31955800},
            {256.52, 258.52, 256.11, 258.06, 36496900},
            {257.81, 258.0, 253.14, 254.04, 38322000},
            {254.94, 256.38, 244.0, 245.27, 61999100},
            {249.38, 249.69, 245.56, 247.66, 38142900},
            {246.60, 248.85, 244.70, 247.77, 35478000},
            {249.49, 251.82, 247.47, 249.34, 33893600},
            {248.25, 249.04, 245.13, 247.45, 39777000},
            {248.02, 253.38, 247.27, 252.29, 49147000},
            {255.89, 264.38, 255.63, 262.24, 90483000},
            {261.88, 265.29, 261.83, 262.77, 46695900},
            {262.65, 262.85, 255.43, 258.45, 45015300},
            {259.94, 260.62, 258.01, 259.58, 32754900},
            {261.19, 264.13, 259.18, 262.82, 38253700},
            {264.88, 269.12, 264.65, 268.81, 44888200},
            {268.99, 269.89, 268.15, 269.0, 41534800},
            {269.28, 271.41, 267.11, 269.70, 51086700},
            {271.99, 274.14, 268.48, 271.40, 69886500},
            {276.99, 277.32, 269.16, 270.37, 86167100},
            {270.42, 270.85, 266.25, 269.05, 50194600},
            {268.33, 271.49, 267.62, 270.04, 49274800},
            {268.61, 271.70, 266.93, 270.14, 43683100},
            {267.89, 273.40, 267.89, 269.77, 51204000},
            {269.80, 272.29, 266.77, 268.47, 48227400},
            {268.96, 273.73, 267.46, 269.43, 41312400},
            {269.81, 275.91, 269.80, 275.25, 46208300},
            {275.0, 275.73, 271.70, 273.47, 48398000},
            {274.11, 276.70, 272.09, 272.95, 49602800},
            {271.05, 275.96, 269.60, 272.41, 47431300},
            {268.82, 270.49, 265.73, 267.46, 45018300},
            {269.99, 270.71, 265.32, 267.44, 45677300},
            {265.53, 272.21, 265.50, 268.56, 40424500},
            {270.83, 275.43, 265.92, 266.25, 45823600},
            {265.95, 273.33, 265.67, 271.49, 59030800},
            {270.90, 277.0, 270.90, 275.92, 65585800},
            {275.27, 280.38, 275.25, 276.97, 46914200},
            {276.96, 279.53, 276.63, 277.55, 33431400},
            {277.26, 279.0, 275.99, 278.85, 20135600},
            {278.01, 283.42, 276.14, 283.10, 46587700},
            {283.0, 287.40, 282.63, 286.19, 53669500},
            {286.20, 288.62, 283.30, 284.15, 43538700},
            {284.10, 284.73, 278.59, 280.70, 43989100},
            {280.54, 281.14, 278.05, 278.78, 47265800},
            {278.13, 279.67, 276.15, 277.89, 38211800},
            {278.16, 280.03, 276.92, 277.18, 32193300},
            {277.75, 279.75, 276.44, 278.78, 33038300},
            {279.10, 279.59, 273.81, 278.03, 33248000},
            {277.90, 279.22, 276.82, 278.28, 39532900},
            {280.15, 280.15, 272.84, 274.11, 50409100},
            {272.82, 275.50, 271.79, 274.61, 37648600},
            {275.01, 276.16, 271.64, 271.84, 50138700},
            {273.61, 273.63, 266.95, 272.19, 51630700},
            {272.15, 274.60, 269.90, 273.67, 144632000},
            {272.86, 273.88, 270.51, 270.97, 36571800},
            {270.84, 272.50, 269.56, 272.36, 29642000},
            {272.34, 275.43, 272.20, 273.81, 17910600},
            {274.16, 275.37, 272.86, 273.40, 21521800},
            {272.69, 274.36, 272.35, 273.76, 23715200},
            {272.81, 274.08, 272.28, 273.08, 22139600},
            {273.06, 273.68, 271.75, 271.86, 27293600},
            {272.26, 277.84, 269.0, 271.01, 37838100},
            {270.64, 271.51, 266.14, 267.26, 45647200},
            {267.0, 267.55, 262.12, 262.36, 52352100},
            {263.20, 263.68, 259.81, 260.33, 48309800},
            {257.02, 259.29, 255.70, 259.04, 50419300},
            {259.08, 260.21, 256.22, 259.37, 39997000},
            {259.16, 261.30, 256.80, 260.25, 45263800},
            {258.72, 261.81, 258.39, 261.05, 45730800},
            {259.49, 261.82, 256.71, 259.96, 40019400},
            {260.65, 261.04, 257.05, 258.21, 39388600},
            {257.90, 258.90, 254.93, 255.53, 72142800},
            {252.73, 254.79, 243.42, 246.70, 80267500},
            {248.70, 251.56, 245.18, 247.65, 54641700},
            {249.20, 251.0, 248.15, 248.35, 39708300},
            {247.32, 249.41, 244.68, 248.04, 41689000},
            {251.48, 256.56, 249.80, 255.41, 55969200},
            {259.17, 261.95, 258.21, 258.27, 49648300},
            {257.65, 258.86, 254.51, 256.44, 41288000},
            {258.0, 259.65, 254.41, 258.28, 67253000},
            {255.17, 261.90, 252.18, 259.48, 92443400},
            {260.03, 270.49, 259.21, 270.01, 73913400},
            {269.20, 271.88, 267.61, 269.48, 64394700},
            {272.29, 278.95, 272.29, 276.49, 90545700},
            {278.13, 279.50, 273.23, 275.91, 52977400},
            {277.12, 280.91, 276.93, 278.12, 50453400},
            {277.91, 278.20, 271.70, 274.62, 44623400},
            {274.89, 275.37, 272.94, 273.68, 34376900},
            {274.70, 280.18, 274.45, 275.50, 51931300},
            {275.59, 275.72, 260.18, 261.73, 81077200},
            {262.01, 262.23, 255.45, 255.78, 56290700},
            {258.05, 266.29, 255.54, 263.88, 58469100},
            {263.60, 266.82, 262.45, 264.35, 34203300},
            {262.60, 264.48, 260.05, 260.58, 30845300},
            {258.97, 264.75, 258.16, 264.58, 42070500},
            {263.49, 269.43, 263.38, 266.18, 37308200},
            {267.86, 274.89, 267.71, 272.14, 47014600},
            {271.78, 274.94, 271.05, 274.23, 33714300},
            {274.95, 276.11, 270.80, 272.95, 32345100},
            {272.81, 272.81, 262.89, 264.18, 72366500},
            {262.41, 266.53, 260.20, 264.72, 41827900},
            {263.48, 265.56, 260.13, 263.75, 38568900},
            {264.65, 266.15, 261.42, 262.52, 39803100},
            {260.79, 261.56, 257.25, 260.29, 49658600},
            {258.63, 258.77, 254.37, 257.46, 41120000},
            {255.69, 261.15, 253.68, 259.88, 38218500},
            {257.65, 262.48, 256.95, 260.83, 30590800},
            {261.09, 262.13, 259.55, 260.81, 26218900},
            {258.66, 258.95, 254.18, 255.76, 40794000},
            {255.48, 256.33, 249.52, 250.12, 36930000},
            {252.11, 253.89, 249.88, 252.82, 32074200},
            {252.96, 255.13, 252.18, 254.23, 32361600},
            {252.63, 254.94, 249.0, 249.94, 35757900},
            {249.40, 251.83, 247.30, 248.96, 34864100},
            {247.98, 249.20, 246.0, 247.99, 88331100},
            {253.97, 254.60, 250.28, 251.49, 40546100},
            {250.35, 254.83, 249.55, 251.64, 45152300},
            {254.10, 255.0, 251.60, 252.62, 28476700},
            {252.12, 257.0, 250.77, 252.89, 41796700},
            {253.90, 255.49, 248.07, 248.80, 47900000},
            {250.07, 250.87, 245.51, 246.63, 39446200},
            {247.91, 255.48, 247.10, 253.79, 49598100},
            {254.08, 256.18, 253.33, 255.63, 40059400},
            {254.20, 256.13, 250.65, 255.92, 31289400},
            {256.51, 262.16, 256.46, 258.86, 29329900},
            {256.16, 256.20, 245.70, 253.50, 62148000},
            {258.45, 259.75, 256.53, 258.90, 41032800},
            {259.0, 261.12, 256.07, 260.49, 28121600},
            {259.98, 262.19, 259.02, 260.48, 31291500},
            {259.86, 260.18, 256.66, 259.20, 35080398},
            {259.36, 261.93, 257.27, 257.27, 570154},
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
