package org.tradelite.quant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.repository.OhlcvRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class VfiService {

    static final int LENGTH = 130;
    static final double COEF = 0.2;
    static final double VCOEF = 2.5;
    static final int SIGNAL_LENGTH = 5;
    static final int VOLATILITY_PERIOD = 30;
    private static final int LOOKBACK_CALENDAR_DAYS = 200;

    private final OhlcvRepository ohlcvRepository;

    public Optional<VfiAnalysis> analyze(String symbol, String displayName) {
        List<OhlcvRecord> records = ohlcvRepository.findBySymbol(symbol, LOOKBACK_CALENDAR_DAYS);

        if (records.size() < VfiAnalysis.MIN_DATA_POINTS) {
            log.debug(
                    "Insufficient OHLCV data for VFI calculation for {}: {} records (need {})",
                    symbol,
                    records.size(),
                    VfiAnalysis.MIN_DATA_POINTS);
            return Optional.empty();
        }

        return Optional.of(calculateVfi(symbol, displayName, records));
    }

    VfiAnalysis calculateVfi(String symbol, String displayName, List<OhlcvRecord> records) {
        int n = records.size();

        // Extract price and volume arrays
        double[] highs = new double[n];
        double[] lows = new double[n];
        double[] closes = new double[n];
        double[] volumes = new double[n];
        for (int i = 0; i < n; i++) {
            OhlcvRecord r = records.get(i);
            highs[i] = r.high();
            lows[i] = r.low();
            closes[i] = r.close();
            volumes[i] = r.volume();
        }

        // Precompute typical prices
        double[] typical = new double[n];
        for (int i = 0; i < n; i++) {
            typical[i] = (highs[i] + lows[i] + closes[i]) / 3.0;
        }

        // Precompute log returns (inter)
        double[] inter = new double[n];
        for (int i = 1; i < n; i++) {
            inter[i] = Math.log(typical[i]) - Math.log(typical[i - 1]);
        }

        // Precompute rolling stdev of log returns (vinter) and cutoff
        double[] cutoff = new double[n];
        for (int i = VOLATILITY_PERIOD; i < n; i++) {
            List<Double> interWindow = new ArrayList<>(VOLATILITY_PERIOD);
            for (int j = i - VOLATILITY_PERIOD + 1; j <= i; j++) {
                interWindow.add(inter[j]);
            }
            double mean = StatisticsUtil.mean(interWindow);
            double vinter = StatisticsUtil.populationStdDev(interWindow, mean);
            cutoff[i] = COEF * vinter * closes[i];
        }

        // Compute rolling VFI values for signal line
        // We need SIGNAL_LENGTH + 1 VFI values for a proper EMA(SIGNAL_LENGTH)
        int numWindows = SIGNAL_LENGTH + 1;
        int lastWindowEnd = n; // exclusive
        int firstWindowStart = lastWindowEnd - LENGTH - (numWindows - 1);

        List<Double> vfiSeries = new ArrayList<>(numWindows);

        for (int w = 0; w < numWindows; w++) {
            int windowStart = firstWindowStart + w;
            int windowEnd = windowStart + LENGTH; // exclusive

            // Lagged vave: mean of volumes from windowStart-1 to windowEnd-1 (exclusive)
            double vave = computeMean(volumes, windowStart - 1, windowEnd - 1);
            if (vave == 0) {
                vfiSeries.add(0.0);
                continue;
            }
            double vmax = vave * VCOEF;

            double vcpSum = 0;
            for (int i = windowStart; i < windowEnd; i++) {
                double vc = Math.min(volumes[i], vmax);
                double mf = typical[i] - typical[i - 1];

                if (mf > cutoff[i]) {
                    vcpSum += vc;
                } else if (mf < -cutoff[i]) {
                    vcpSum -= vc;
                }
            }

            vfiSeries.add(vcpSum / vave);
        }

        double vfiValue = StatisticsUtil.roundTo2Decimals(vfiSeries.getLast());
        double signalValue =
                StatisticsUtil.roundTo2Decimals(
                        StatisticsUtil.calculateEma(vfiSeries, SIGNAL_LENGTH));

        return new VfiAnalysis(symbol, displayName, vfiValue, signalValue);
    }

    private static double computeMean(double[] values, int fromInclusive, int toExclusive) {
        if (toExclusive <= fromInclusive) {
            return 0;
        }
        double sum = 0;
        for (int i = fromInclusive; i < toExclusive; i++) {
            sum += values[i];
        }
        return sum / (toExclusive - fromInclusive);
    }
}
