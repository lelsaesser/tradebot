package org.tradelite.client.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiscordMessageResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("channel_id")
    private String channelId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("timestamp")
    private String timestamp;
}
