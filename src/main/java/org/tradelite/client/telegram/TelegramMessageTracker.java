package org.tradelite.client.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
public class TelegramMessageTracker {

    private Path filePath;

    @Autowired
    public TelegramMessageTracker() {
        this.filePath = Paths.get("config/tg-last-processed-message-id.txt");
    }

    public TelegramMessageTracker(Path filePath) {
        this.filePath = filePath;
    }

    public long getLastProcessedMessageId() {
        try {
            return Integer.parseInt(Files.readString(filePath).trim());
        } catch (IOException | NumberFormatException e) {
            log.error("Failed to read last processed message ID from file: {}", filePath);
            throw new IllegalStateException(e);
        }
    }

    public void setLastProcessedMessageId(long messageId) {
        try {
            Files.writeString(filePath, String.valueOf(messageId), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write last message ID", e);
        }
    }
}
