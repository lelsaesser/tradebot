package org.tradelite.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response from Telegram Bot API sendMessage endpoint.
 *
 * <p>The sendMessage API returns a JSON object with an "ok" boolean and a "result" containing the
 * sent message details including the message_id.
 */
@Data
public class TelegramSendMessageResponse {

    @JsonProperty("ok")
    private boolean ok;

    @JsonProperty("result")
    private TelegramMessage result;
}
