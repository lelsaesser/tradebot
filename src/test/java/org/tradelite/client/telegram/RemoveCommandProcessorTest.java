package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPriceProvider;

@ExtendWith(MockitoExtension.class)
class RemoveCommandProcessorTest {

    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramGateway telegramClient;
    @Mock private SymbolRegistry symbolRegistry;

    private RemoveCommandProcessor removeCommandProcessor;

    @BeforeEach
    void setUp() {
        removeCommandProcessor =
                new RemoveCommandProcessor(targetPriceProvider, telegramClient, symbolRegistry);
    }

    @Test
    void canProcess_removeCommand_returnsTrue() {
        RemoveCommand command = new RemoveCommand("PLTR");
        boolean result = removeCommandProcessor.canProcess(command);

        assertThat(result, is(true));
    }

    @Test
    void canProcess_nonRemoveCommand_returnsFalse() {
        SetCommand command = new SetCommand("buy", "BITCOIN", 50000.0);
        boolean result = removeCommandProcessor.canProcess(command);

        assertThat(result, is(false));
    }

    @Test
    void processCommand_validRemoveCommand_removesSymbol() {
        RemoveCommand command = new RemoveCommand("PLTR");
        when(symbolRegistry.removeSymbol("PLTR")).thenReturn(true);
        when(targetPriceProvider.removeSymbolFromTargetPrices(eq("PLTR"), anyString()))
                .thenReturn(true);

        removeCommandProcessor.processCommand(command);

        verify(symbolRegistry).removeSymbol("PLTR");
        verify(targetPriceProvider).removeSymbolFromTargetPrices(eq("PLTR"), anyString());
        verify(telegramClient).sendMessage("Removed PLTR from monitoring.");
    }

    @Test
    void processCommand_symbolNotFound_sendsErrorMessage() {
        RemoveCommand command = new RemoveCommand("NONEXISTENT");
        when(symbolRegistry.removeSymbol("NONEXISTENT")).thenReturn(false);
        when(targetPriceProvider.removeSymbolFromTargetPrices(eq("NONEXISTENT"), anyString()))
                .thenReturn(false);

        removeCommandProcessor.processCommand(command);

        verify(telegramClient).sendMessage("Symbol NONEXISTENT not found or already removed.");
    }

    @Test
    void processCommand_onlyTargetPriceRemoved_sendsSuccessMessage() {
        RemoveCommand command = new RemoveCommand("TEST");
        when(symbolRegistry.removeSymbol("TEST")).thenReturn(false);
        when(targetPriceProvider.removeSymbolFromTargetPrices(eq("TEST"), anyString()))
                .thenReturn(true);

        removeCommandProcessor.processCommand(command);

        verify(telegramClient).sendMessage("Removed TEST from monitoring.");
    }

    @Test
    void processCommand_onlySymbolRemoved_sendsSuccessMessage() {
        RemoveCommand command = new RemoveCommand("PLTR");
        when(symbolRegistry.removeSymbol("PLTR")).thenReturn(true);
        when(targetPriceProvider.removeSymbolFromTargetPrices(eq("PLTR"), anyString()))
                .thenReturn(false);

        removeCommandProcessor.processCommand(command);

        verify(telegramClient).sendMessage("Removed PLTR from monitoring.");
    }
}
