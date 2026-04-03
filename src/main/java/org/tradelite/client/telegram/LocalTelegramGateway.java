package org.tradelite.client.telegram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.config.TradebotTelegramProperties;

@Slf4j
@Component
@Primary
@Profile("dev")
public class LocalTelegramGateway implements TelegramGateway {

    private static final AtomicLong MESSAGE_IDS = new AtomicLong(1L);

    private final Path sinkFilePath;

    public LocalTelegramGateway(TradebotTelegramProperties properties) {
        this.sinkFilePath = Path.of(properties.getLocalSinkFile());
    }

    @Override
    public void sendMessage(String message) {
        sendMessageAndReturnId(message);
    }

    @Override
    public OptionalLong sendMessageAndReturnId(String message) {
        long messageId = MESSAGE_IDS.getAndIncrement();
        log.info("DEV Telegram sink [{}]: {}", messageId, message);
        try {
            Path parent = sinkFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line =
                    String.format(
                            "%s [%d] %s%s",
                            Instant.now(), messageId, message, System.lineSeparator());
            Files.writeString(
                    sinkFilePath,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            return OptionalLong.of(messageId);
        } catch (IOException e) {
            log.warn("Failed to write dev telegram sink file {}: {}", sinkFilePath, e.getMessage());
            return OptionalLong.empty();
        }
    }

    @Override
    public void deleteMessage(long messageId) {
        log.info("DEV Telegram delete noop for message {}", messageId);
    }

    @Override
    public List<TelegramUpdateResponse> getChatUpdates() {
        return Collections.emptyList();
    }
}
