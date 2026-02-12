package org.tradelite.client.telegram;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void sendMessage_whenSinkPathIsDirectory_doesNotThrow() throws Exception {
        Path directorySink = Files.createTempDirectory("telegram-dev-dir");

        TradebotTelegramProperties properties = new TradebotTelegramProperties();
        properties.setLocalSinkFile(directorySink.toString());

        LocalTelegramGateway gateway = new LocalTelegramGateway(properties);
        gateway.sendMessage("this should hit io catch");

        assertThat(Files.isDirectory(directorySink), is(true));
    }
}
