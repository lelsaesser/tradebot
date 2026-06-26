package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.service.SymbolManagementService;
import org.tradelite.service.SymbolManagementService.AddResult;

@ExtendWith(MockitoExtension.class)
class AddCommandProcessorTest {

    @Mock private SymbolManagementService symbolManagementService;
    @Mock private TelegramGateway telegramClient;

    private AddCommandProcessor addCommandProcessor;

    @BeforeEach
    void setUp() {
        addCommandProcessor = new AddCommandProcessor(symbolManagementService, telegramClient);
    }

    @Test
    void canProcess_addCommand_returnsTrue() {
        AddCommand command = new AddCommand("AAPL", "Apple", 0, 0);
        assertThat(addCommandProcessor.canProcess(command), is(true));
    }

    @Test
    void canProcess_nonAddCommand_returnsFalse() {
        SetCommand command = new SetCommand("buy", "BITCOIN", 50000.0);
        assertThat(addCommandProcessor.canProcess(command), is(false));
    }

    @Test
    void processCommand_success_sendsSuccessMessage() {
        AddCommand command = new AddCommand("COHR", "Coherent Corp", 100.0, 150.0);
        when(symbolManagementService.addSymbol(eq("COHR"), eq("Coherent Corp"), any(), any()))
                .thenReturn(new AddResult(true, "Added Coherent Corp (COHR)."));

        addCommandProcessor.processCommand(command);

        verify(telegramClient)
                .sendMessage(
                        "All set!\nAdded Coherent Corp (COHR) with buy target 100.0 and sell target 150.0.");
    }

    @Test
    void processCommand_failure_sendsErrorMessage() {
        AddCommand command = new AddCommand("INVALID", "Invalid", 0.0, 0.0);
        when(symbolManagementService.addSymbol(eq("INVALID"), eq("Invalid"), any(), any()))
                .thenReturn(new AddResult(false, "Invalid ticker: INVALID."));

        addCommandProcessor.processCommand(command);

        verify(telegramClient).sendMessage("Invalid ticker: INVALID.");
    }
}
