package org.tradelite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tradebot.telegram")
public class TradebotTelegramProperties {

    private String botToken;
    private String groupChatId;
    private String localSinkFile = "config/dev-telegram-messages.log";

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getGroupChatId() {
        return groupChatId;
    }

    public void setGroupChatId(String groupChatId) {
        this.groupChatId = groupChatId;
    }

    public String getLocalSinkFile() {
        return localSinkFile;
    }

    public void setLocalSinkFile(String localSinkFile) {
        this.localSinkFile = localSinkFile;
    }
}
