package org.tradelite.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TelegramUser {

    @JsonProperty("id")
    private Long userId;

    @JsonProperty("is_bot")
    private boolean isBot;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("language_code")
    private String languageCode;
}
