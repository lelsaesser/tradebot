package org.tradelite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tradebot.api")
public class TradebotApiProperties {

    private String finnhubKey;
    private String coingeckoKey;

    public String getFinnhubKey() {
        return finnhubKey;
    }

    public void setFinnhubKey(String finnhubKey) {
        this.finnhubKey = finnhubKey;
    }

    public String getCoingeckoKey() {
        return coingeckoKey;
    }

    public void setCoingeckoKey(String coingeckoKey) {
        this.coingeckoKey = coingeckoKey;
    }
}
