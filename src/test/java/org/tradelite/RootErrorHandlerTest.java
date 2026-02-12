package org.tradelite;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;

@ExtendWith(MockitoExtension.class)
class RootErrorHandlerTest {

    @Mock private TelegramGateway telegramClient;

    private RootErrorHandler rootErrorHandler;

    @BeforeEach
    void setUp() {
        rootErrorHandler = new RootErrorHandler(telegramClient);
    }

    @Test
    void testRun_withInterruptedException() {
        rootErrorHandler.run(
                () -> {
                    throw new InterruptedException();
                });

        String expectedMessage = "⏸️ *Operation Interrupted!* Check application logs for details.";
        verify(telegramClient).sendMessage(expectedMessage);
    }

    @Test
    void testRun_withException() {
        rootErrorHandler.run(
                () -> {
                    throw new RuntimeException("Test exception");
                });
        verify(telegramClient).sendMessage(anyString());
    }

    @Test
    void testRun_withNoException() {
        rootErrorHandler.run(
                () -> {
                    // No exception thrown
                });

        verify(telegramClient, never()).sendMessage(anyString());
    }
}
