package org.tradelite.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IgnoreReason {
    BUY_ALERT("buy_alert", 3600L * 12),
    SELL_ALERT("sell_alert", 3600L * 12),
    CHANGE_PERCENT_ALERT("change_percent_alert", 3600L * 12),

    /**
     * 8h cooldown: effectively once per trading day (6.5h session), allows re-alert next morning
     */
    PULLBACK_BUY_ALERT("pullback_buy_alert", 3600L * 8);

    private final String reason;
    private final long ttlSeconds;
}
