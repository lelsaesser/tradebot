package org.tradelite.common;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum CoinId implements TickerSymbol {
    BITCOIN("bitcoin"),
    SOLANA("solana"),
    HYPERLIQUID("hyperliquid"),
    SUI("sui"),
    // POLKADOT("polkadot"),
    DOGE("doge"),
    ETHEREUM("ethereum");

    private final String id;

    CoinId(String id) {
        this.id = id;
    }

    public static List<CoinId> getAll() {
        return List.of(CoinId.values());
    }

    public static Optional<CoinId> fromString(String input) {
        return Arrays.stream(CoinId.values())
                .filter(symbol -> symbol.getName().equalsIgnoreCase(input))
                .findFirst();
    }

    @Override
    public String getName() {
        return id;
    }

    @Override
    public SymbolType getSymbolType() {
        return SymbolType.CRYPTO;
    }

    @Override
    public String getDisplayName() {
        return id;
    }
}
