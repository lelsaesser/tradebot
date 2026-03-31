package org.tradelite.client.telegram;

import java.util.List;
import java.util.OptionalLong;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;

public interface TelegramGateway {
    void sendMessage(String message);

    OptionalLong sendMessageAndReturnId(String message);

    void deleteMessage(long messageId);

    List<TelegramUpdateResponse> getChatUpdates();
}
