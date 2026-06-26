package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.service.SymbolManagementService;

@ExtendWith(MockitoExtension.class)
class RemoveCommandProcessorTest {

    @Mock private SymbolManagementService symbolManagementService;
    @Mock private TelegramGateway telegramClient;

    private RemoveCommandProcessor removeCommandProcessor;

    @BeforeEach
    void setUp() {
        removeCommandProcessor =
                new RemoveCommandProcessor(symbolManagementService, telegramClient);
    }

    @Test
    void canProcess_removeCommand_returnsTrue() {
        RemoveCommand command = new RemoveCommand("PLTR");
        assertThat(removeCommandProcessor.canProcess(command), is(true));
    }

    @Test
    void canProcess_nonRemoveCommand_returnsFalse() {
        SetCommand command = new SetCommand("buy", "BITCOIN", 50000.0);
        assertThat(removeCommandProcessor.canProcess(command), is(false));
    }

    @Test
    void processCommand_success_sendsSuccessMessage() {
        RemoveCommand command = new RemoveCommand("PLTR");
        when(symbolManagementService.removeSymbol("PLTR")).thenReturn(true);

        removeCommandProcessor.processCommand(command);

        verify(telegramClient).sendMessage("Removed PLTR from monitoring.");
    }

    @Test
    void processCommand_symbolNotFound_sendsErrorMessage() {
        RemoveCommand command = new RemoveCommand("NONEXISTENT");
        when(symbolManagementService.removeSymbol("NONEXISTENT")).thenReturn(false);

        removeCommandProcessor.processCommand(command);

        verify(telegramClient).sendMessage("Symbol NONEXISTENT not found or already removed.");
    }
}
