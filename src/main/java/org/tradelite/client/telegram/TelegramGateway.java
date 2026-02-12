package org.tradelite.client.telegram;

import java.util.List;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;

public interface TelegramGateway {
    void sendMessage(String message);

    List<TelegramUpdateResponse> getChatUpdates();
}
