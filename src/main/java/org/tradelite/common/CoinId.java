package org.tradelite.common;

import lombok.Getter;

import java.util.List;

@Getter
public enum CoinId implements TickerSymbol {
    BITCOIN("bitcoin");

    private final String id;

    CoinId(String id) {
        this.id = id;
    }

    public static List<CoinId> getAll() {
        return List.of(CoinId.values());
    }
}
