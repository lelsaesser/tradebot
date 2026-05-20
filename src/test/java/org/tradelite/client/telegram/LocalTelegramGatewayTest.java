package org.tradelite.client.telegram;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.tradelite.config.TradebotTelegramProperties;

class LocalTelegramGatewayTest {

    @Test
    void sendMessage_writesToSinkFileAndReturnsNoUpdates() throws Exception {
        Path sinkFile = Files.createTempFile("telegram-dev-sink", ".log");
        Files.deleteIfExists(sinkFile);

        TradebotTelegramProperties properties = new TradebotTelegramProperties();
        properties.setLocalSinkFile(sinkFile.toString());

        LocalTelegramGateway gateway = new LocalTelegramGateway(properties);
        gateway.sendMessage("hello-dev");

        assertThat(Files.exists(sinkFile), is(true));
        String content = Files.readString(sinkFile);
        assertThat(content.contains("hello-dev"), is(true));
        assertThat(gateway.getChatUpdates(), notNullValue());
        assertThat(gateway.getChatUpdates().isEmpty(), is(true));
    }

    @Test
    void sendMessageAndReturnId_returnsSyntheticMessageId() throws Exception {
        Path sinkFile = Files.createTempFile("telegram-dev-sink-id", ".log");
        Files.deleteIfExists(sinkFile);

        TradebotTelegramProperties properties = new TradebotTelegramProperties();
        properties.setLocalSinkFile(sinkFile.toString());

        LocalTelegramGateway gateway = new LocalTelegramGateway(properties);
        OptionalLong result = gateway.sendMessageAndReturnId("hello-dev");

        assertThat(result.isPresent(), is(true));
        assertThat(result.getAsLong(), is(not(0L)));
    }

    @Test
    void deleteMessage_isNoOp() throws Exception {
        Path sinkFile = Files.createTempFile("telegram-dev-sink-delete", ".log");
        TradebotTelegramProperties properties = new TradebotTelegramProperties();
        properties.setLocalSinkFile(sinkFile.toString());

        LocalTelegramGateway gateway = new LocalTelegramGateway(properties);
        gateway.deleteMessage(42L);

        assertThat(Files.exists(sinkFile), is(true));
    }

    @Test
    void sendMessage_whenSinkPathIsDirectory_doesNotThrow() throws Exception {
        Path directorySink = Files.createTempDirectory("telegram-dev-dir");

        TradebotTelegramProperties properties = new TradebotTelegramProperties();
        properties.setLocalSinkFile(directorySink.toString());

        LocalTelegramGateway gateway = new LocalTelegramGateway(properties);
        gateway.sendMessage("this should hit io catch");

        assertThat(Files.isDirectory(directorySink), is(true));
    }

    @Test
    void sendMessage_whenOverLimitButFitsAfterStrip_writesStrippedPayload() throws Exception {
        Path sinkFile = Files.createTempFile("telegram-dev-strip", ".log");
        Files.deleteIfExists(sinkFile);

        TradebotTelegramProperties properties = new TradebotTelegramProperties();
        properties.setLocalSinkFile(sinkFile.toString());

        // Message > 4096 chars that fits after stripping markers.
        String plain = "x".repeat(TelegramMessageSanitizer.LIMIT - 100);
        String markers = "*".repeat(200);
        String oversized = plain + markers;
        assertThat(oversized.length() > TelegramMessageSanitizer.LIMIT, is(true));

        LocalTelegramGateway gateway = new LocalTelegramGateway(properties);
        gateway.sendMessage(oversized);

        String content = Files.readString(sinkFile);
        // Stripping should have removed all markdown markers.
        assertThat(content.contains("*"), is(false));
        assertThat(content.contains("`"), is(false));
        assertThat(content.contains("_"), is(false));
    }

    @Test
    void sendMessage_whenOverLimitAfterStrip_writesTruncatedPayload() throws Exception {
        Path sinkFile = Files.createTempFile("telegram-dev-trunc", ".log");
        Files.deleteIfExists(sinkFile);

        TradebotTelegramProperties properties = new TradebotTelegramProperties();
        properties.setLocalSinkFile(sinkFile.toString());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("row-").append(i).append("\n");
        }
        String oversized = sb.toString();
        assertThat(oversized.length() > TelegramMessageSanitizer.LIMIT, is(true));

        LocalTelegramGateway gateway = new LocalTelegramGateway(properties);
        gateway.sendMessage(oversized);

        String content = Files.readString(sinkFile);
        // Sink line is "<timestamp> [<id>] <payload>\n" — payload portion must be <= LIMIT.
        // Verify the file content is much smaller than the original message.
        assertThat(content.length() < oversized.length(), is(true));
    }
}
