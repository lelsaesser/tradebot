package org.tradelite.core;

import lombok.Getter;

@Getter
public enum InsiderTransactionCodes {

    SELL("S"),
    SELL_HISTORIC("S_HISTORIC");

    private final String code;

    InsiderTransactionCodes(String code) {
        this.code = code;
    }
}
