package org.tradelite.client.telegram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.config.TradebotTelegramProperties;

@Slf4j
@Component
@Profile("!prod")
public class LocalTelegramGateway implements TelegramGateway {

    private final Path sinkFilePath;

    public LocalTelegramGateway(TradebotTelegramProperties properties) {
        this.sinkFilePath = Path.of(properties.getLocalSinkFile());
    }

    @Override
    public void sendMessage(String message) {
        log.info("DEV Telegram sink: {}", message);
        try {
            Path parent = sinkFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = Instant.now() + " " + message + System.lineSeparator();
            Files.writeString(
                    sinkFilePath,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to write dev telegram sink file {}: {}", sinkFilePath, e.getMessage());
        }
    }

    @Override
    public List<TelegramUpdateResponse> getChatUpdates() {
        return Collections.emptyList();
    }
}
