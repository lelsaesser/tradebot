package org.tradelite.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.tradelite.common.TickerSymbol;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@AllArgsConstructor
public class IgnoredSymbol {

    private TickerSymbol symbol;
    private Map<IgnoreReason, Instant> ignoreTimes;
    private Map<IgnoreReason, Integer> alertThresholds;

    public IgnoredSymbol(TickerSymbol symbol) {
        this.symbol = symbol;
        this.ignoreTimes = new ConcurrentHashMap<>();
        this.alertThresholds = new ConcurrentHashMap<>();
    }
}
