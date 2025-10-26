package org.tradelite.core;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.tradelite.common.TickerSymbol;

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
