package org.tradelite.client.telegram;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.config.ConfigurationService;

@ExtendWith(MockitoExtension.class)
class EnableCommandProcessorTest {

    @Mock private TelegramClient telegramClient;

    @Mock private ConfigurationService configurationService;

    @InjectMocks private EnableCommandProcessor processor;

    @Test
    void processCommand_demoTrading_shouldEnableAndNotify() {
        EnableCommand command = new EnableCommand("demotrading");

        processor.processCommand(command);

        verify(configurationService, times(1)).enable("demotrading");
        verify(telegramClient, times(1)).sendMessage(contains("Demo Trading enabled"));
    }

    @Test
    void processCommand_unknownFeature_shouldNotifyError() {
        EnableCommand command = new EnableCommand("unknown");

        processor.processCommand(command);

        verify(configurationService, never()).enable(anyString());
        verify(telegramClient, times(1)).sendMessage(contains("Unknown feature"));
    }

    @Test
    void canProcess_enableCommand_shouldReturnTrue() {
        EnableCommand command = new EnableCommand("demotrading");

        boolean result = processor.canProcess(command);

        assertThat(result, is(true));
    }

    @Test
    void canProcess_otherCommand_shouldReturnFalse() {
        DisableCommand command = new DisableCommand("demotrading");

        boolean result = processor.canProcess(command);

        assertThat(result, is(false));
    }
}
