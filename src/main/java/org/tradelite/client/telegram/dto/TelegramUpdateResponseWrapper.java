package org.tradelite.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TelegramUpdateResponseWrapper {

    @JsonProperty("ok")
    private boolean ok;
    @JsonProperty("result")
    private List<TelegramUpdateResponse> result;
}
