package org.tradelite.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IgnoreReason {
    BUY_ALERT("buy_alert"),
    SELL_ALERT("sell_alert"),
    CHANGE_PERCENT_ALERT("change_percent_alert");

    private final String reason;
}
