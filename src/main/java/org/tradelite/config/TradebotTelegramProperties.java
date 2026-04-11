package org.tradelite.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "tradebot.telegram")
public class TradebotTelegramProperties {

    private String botToken;
    private String groupChatId;
    private String localSinkFile = "config/dev-telegram-messages.log";
}
