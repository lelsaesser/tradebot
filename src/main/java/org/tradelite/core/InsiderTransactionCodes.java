package org.tradelite.core;

import lombok.Getter;

@Getter
public enum InsiderTransactionCodes {

    SELL("S"),
    SELL_HISTORIC("S_HISTORIC"),
    BUY("P"),
    BUY_HISTORIC("P_HISTORIC"),
    SELL_VOLUNTARY_REPORT("S/V"),
    BUY_VOLUNTARY_REPORT("P/V");

    private final String code;

    InsiderTransactionCodes(String code) {
        this.code = code;
    }
}
