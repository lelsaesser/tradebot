package org.tradelite.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TelegramMessage {

    @JsonProperty("message_id")
    private Long messageId;
    @JsonProperty("from")
    private TelegramUser from;
    @JsonProperty("chat")
    private TelegramChat chat;
    @JsonProperty("date")
    private Long date;
    @JsonProperty("text")
    private String text;
    @JsonProperty("entities")
    private List<MessageEntity> entities;
}
