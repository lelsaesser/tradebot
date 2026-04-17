package org.tradelite.service;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StockSplitDetector {

    static final double TOLERANCE = 0.05;
    static final int[] COMMON_SPLIT_FACTORS = {2, 3, 4, 5, 7, 8, 10, 15, 20, 25, 50};

    public record SplitResult(int factor, Direction direction) {

        public enum Direction {
            FORWARD,
            REVERSE
        }
    }

    /**
     * Detects if a price ratio between a stored close and a fetched close matches a common stock
     * split factor within tolerance.
     *
     * @param lastStoredClose the most recent close price from the DB
     * @param oldestFetchedClose the oldest close price in the newly fetched batch
     * @return the detected split result with factor and direction, or empty
     */
    public Optional<SplitResult> detectSplit(double lastStoredClose, double oldestFetchedClose) {
        if (lastStoredClose <= 0 || oldestFetchedClose <= 0) {
            return Optional.empty();
        }

        double forwardRatio = lastStoredClose / oldestFetchedClose;
        for (int factor : COMMON_SPLIT_FACTORS) {
            if (Math.abs(forwardRatio - factor) / factor <= TOLERANCE) {
                return Optional.of(new SplitResult(factor, SplitResult.Direction.FORWARD));
            }
        }

        double reverseRatio = oldestFetchedClose / lastStoredClose;
        for (int factor : COMMON_SPLIT_FACTORS) {
            if (Math.abs(reverseRatio - factor) / factor <= TOLERANCE) {
                return Optional.of(new SplitResult(factor, SplitResult.Direction.REVERSE));
            }
        }

        return Optional.empty();
    }
}
