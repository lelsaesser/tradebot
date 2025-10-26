package org.tradelite.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class TelegramUpdateResponseWrapper {

    @JsonProperty("ok")
    private boolean ok;

    @JsonProperty("result")
    private List<TelegramUpdateResponse> result;
}
