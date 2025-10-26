package org.tradelite.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TelegramChat {

    @JsonProperty("id")
    private Long chatId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("type")
    private String type;
}
