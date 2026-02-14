package org.tradelite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tradebot.api")
public class TradebotApiProperties {

    private String finnhubKey;
    private String coingeckoKey;
    private String fixtureBasePath = "config/dev-fixtures";

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

    public String getFixtureBasePath() {
        return fixtureBasePath;
    }

    public void setFixtureBasePath(String fixtureBasePath) {
        this.fixtureBasePath = fixtureBasePath;
    }
}
