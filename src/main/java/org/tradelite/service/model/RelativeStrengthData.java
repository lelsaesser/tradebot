package org.tradelite.service.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;

/**
 * Stores relative strength (RS) history for a stock vs SPY benchmark. Used to calculate EMA and
 * detect crossovers.
 */
@Data
public class RelativeStrengthData {

    /** Historical RS values with dates */
    private List<DailyPrice> rsHistory = new ArrayList<>();

    /** Previous RS value (for crossover detection) */
    private double previousRs;

    /** Previous EMA value (for crossover detection) */
    private double previousEma;

    /** Whether we've detected at least one crossover state change */
    private boolean initialized;

    /**
     * Adds a new RS value for a specific date.
     *
     * @param date The date of the RS value
     * @param rsValue The relative strength value (stock price / SPY price)
     */
    public void addRsValue(LocalDate date, double rsValue) {
        Optional<DailyPrice> existingPrice =
                rsHistory.stream().filter(p -> p.getDate().equals(date)).findFirst();

        if (existingPrice.isPresent()) {
            existingPrice.get().setPrice(rsValue);
        } else {
            DailyPrice newPrice = new DailyPrice();
            newPrice.setDate(date);
            newPrice.setPrice(rsValue);
            rsHistory.add(newPrice);
        }

        // Keep the latest 200 RS values
        if (rsHistory.size() > 200) {
            rsHistory.sort((p1, p2) -> p1.getDate().compareTo(p2.getDate()));
            rsHistory.removeFirst();
        }
    }

    /**
     * Gets RS values sorted chronologically (oldest first).
     *
     * @return List of RS values in chronological order
     */
    @JsonIgnore
    public List<Double> getRsValues() {
        rsHistory.sort((p1, p2) -> p1.getDate().compareTo(p2.getDate()));
        List<Double> rsValues = new ArrayList<>();
        for (DailyPrice price : rsHistory) {
            rsValues.add(price.getPrice());
        }
        return rsValues;
    }

    /**
     * Gets the most recent RS value.
     *
     * @return The most recent RS value, or 0 if no data
     */
    @JsonIgnore
    public double getLatestRs() {
        if (rsHistory.isEmpty()) {
            return 0;
        }
        rsHistory.sort((p1, p2) -> p2.getDate().compareTo(p1.getDate()));
        return rsHistory.getFirst().getPrice();
    }
}
