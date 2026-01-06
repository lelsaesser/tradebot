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
class DisableCommandProcessorTest {

    @Mock private TelegramClient telegramClient;

    @Mock private ConfigurationService configurationService;

    @InjectMocks private DisableCommandProcessor processor;

    @Test
    void processCommand_demoTrading_shouldDisableAndNotify() {
        DisableCommand command = new DisableCommand("demotrading");

        processor.processCommand(command);

        verify(configurationService, times(1)).disable("demotrading");
        verify(telegramClient, times(1)).sendMessage(contains("Demo Trading disabled"));
    }

    @Test
    void processCommand_unknownFeature_shouldNotifyError() {
        DisableCommand command = new DisableCommand("unknown");

        processor.processCommand(command);

        verify(configurationService, never()).disable(anyString());
        verify(telegramClient, times(1)).sendMessage(contains("Unknown feature"));
    }

    @Test
    void canProcess_disableCommand_shouldReturnTrue() {
        DisableCommand command = new DisableCommand("demotrading");

        boolean result = processor.canProcess(command);

        assertThat(result, is(true));
    }

    @Test
    void canProcess_otherCommand_shouldReturnFalse() {
        EnableCommand command = new EnableCommand("demotrading");

        boolean result = processor.canProcess(command);

        assertThat(result, is(false));
    }
}
