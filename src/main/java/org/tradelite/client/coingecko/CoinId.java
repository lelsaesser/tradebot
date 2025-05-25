package org.tradelite.client.coingecko;

import lombok.Getter;

@Getter
public enum CoinId {
    BITCOIN("bitcoin"),
    ETHEREUM("ethereum"),
    TETHER("tether"),
    BINANCE_COIN("binancecoin"),
    USD_COIN("usd-coin"),
    XRP("xrp"),
    CARDANO("cardano"),
    DOGECOIN("dogecoin"),
    POLKADOT("polkadot"),
    TRON("tron");

    private final String id;

    CoinId(String id) {
        this.id = id;
    }
}
