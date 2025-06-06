package org.tradelite.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.tradelite.common.TickerSymbol;

import java.time.Instant;
import java.util.Map;

@Data
@AllArgsConstructor
public class IgnoredSymbol {

    private TickerSymbol symbol;
    private Map<IgnoreReason, Instant> ignoreReasons;
}
