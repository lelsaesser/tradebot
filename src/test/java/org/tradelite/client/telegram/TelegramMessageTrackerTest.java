package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelegramMessageTrackerTest {

    private TelegramMessageTracker tracker;

    @TempDir Path tempDir;

    private Path filePath;

    @BeforeEach
    void setUp() throws IOException {
        filePath = tempDir.resolve("tg-last-processed-message-id.txt");
        tracker = new TelegramMessageTracker(filePath);
        Files.writeString(filePath, "12345");
    }

    @Test
    void getLastProcessedMessageId_success() {
        assertThat(tracker.getLastProcessedMessageId(), is(12345L));
    }

    @Test
    void setLastProcessedMessageId_success() {
        tracker.setLastProcessedMessageId(54321);
        assertThat(tracker.getLastProcessedMessageId(), is(54321L));
    }

    @Test
    void getLastProcessedMessageId_fileNotFound() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    new TelegramMessageTracker(Path.of("non-existent-file"))
                            .getLastProcessedMessageId();
                });
    }

    @Test
    void setLastProcessedMessageId_ioException() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    tracker.setLastProcessedMessageId(1L);
                    Files.setPosixFilePermissions(
                            filePath,
                            java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
                    tracker.setLastProcessedMessageId(2L);
                });
    }

    @Test
    void testDefaultConstructor() throws IOException {
        Path path = Path.of("config/tg-last-processed-message-id.txt");
        String content = "";
        if (Files.exists(path)) {
            content = Files.readString(path);
            Files.delete(path);
        }

        TelegramMessageTracker defaultTracker = new TelegramMessageTracker();
        assertThrows(IllegalStateException.class, defaultTracker::getLastProcessedMessageId);

        if (!content.isEmpty()) {
            Files.writeString(path, content);
        }
    }
}
