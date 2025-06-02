package org.tradelite.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MessageEntity {

    @JsonProperty("offset")
    private int offset;
    @JsonProperty("length")
    private int length;
    @JsonProperty("type")
    private String type;
}
