package org.tradelite.client.telegram;

import java.util.List;
import java.util.OptionalLong;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;

public interface TelegramGateway {
    /**
     * Sends a message. Implementations MUST NOT throw — failures are logged and swallowed so
     * callers can rely on send-and-forget semantics.
     */
    void sendMessage(String message);

    /**
     * Sends a message and returns the message ID. Implementations MUST NOT throw — failures are
     * logged and swallowed; an empty {@link OptionalLong} indicates the message was not sent.
     */
    OptionalLong sendMessageAndReturnId(String message);

    void deleteMessage(long messageId);

    List<TelegramUpdateResponse> getChatUpdates();
}
