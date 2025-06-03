package org.tradelite.client.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
public class TelegramMessageTracker {

    private static final String FILE_STORAGE_PATH = "config/tg-last-processed-message-id.txt";

    public long getLastProcessedMessageId() {
        try {
            Path path = Paths.get(FILE_STORAGE_PATH);
            if (!path.toFile().exists()) {
                throw new FileNotFoundException(FILE_STORAGE_PATH);
            }
            return Integer.parseInt(Files.readString(path).trim());
        } catch (IOException | NumberFormatException e) {
            log.error("Failed to read last processed message ID from file: {}", FILE_STORAGE_PATH);
            throw new IllegalStateException(e);
        }
    }

    public void setLastProcessedMessageId(long messageId) {
        try {
            Files.writeString(Paths.get(FILE_STORAGE_PATH), String.valueOf(messageId), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write last message ID", e);
        }
    }
}
