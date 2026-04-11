package org.tradelite.config;

import lombok.getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "tradebot.api")
public class TradebotApiProperties {

    private String finnhubKey;
    private String coingeckoKey;
}
