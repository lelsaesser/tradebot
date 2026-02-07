package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.StockSymbolRegistry;

@ExtendWith(MockitoExtension.class)
class AddCommandProcessorTest {

    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramClient telegramClient;
    @Mock private StockSymbolRegistry stockSymbolRegistry;

    private AddCommandProcessor addCommandProcessor;

    @BeforeEach
    void setUp() {
        addCommandProcessor =
                new AddCommandProcessor(targetPriceProvider, telegramClient, stockSymbolRegistry);
    }

    @Test
    void canProcess_addCommand_returnsTrue() {
        AddCommand command = new AddCommand("AAPL", "Apple", 0, 0);
        boolean result = addCommandProcessor.canProcess(command);

        assertThat(result, is(true));
    }

    @Test
    void canProcess_nonAddCommand_returnsFalse() {
        SetCommand command = new SetCommand("buy", "BITCOIN", 50000.0);
        boolean result = addCommandProcessor.canProcess(command);

        assertThat(result, is(false));
    }

    @Test
    void processCommand_validAddCommand_updatesTargetPrice() {
        AddCommand command = new AddCommand("COHR", "Coherent Corp", 0.0, 0.0);
        when(stockSymbolRegistry.addSymbol("COHR", "Coherent Corp")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), anyString()))
                .thenReturn(true);

        addCommandProcessor.processCommand(command);

        verify(stockSymbolRegistry).addSymbol("COHR", "Coherent Corp");
        verify(targetPriceProvider).addTargetPrice(any(TargetPrice.class), anyString());
        verify(telegramClient)
                .sendMessage(
                        "All set!\nAdded Coherent Corp (COHR) with buy target 0.0 and sell target 0.0.");
    }

    @Test
    void processCommand_symbolAlreadyExists_sendsErrorMessage() {
        AddCommand command = new AddCommand("AAPL", "Apple", 0.0, 0.0);
        when(stockSymbolRegistry.addSymbol("AAPL", "Apple")).thenReturn(false);

        addCommandProcessor.processCommand(command);

        verify(telegramClient)
                .sendMessage(
                        "Failed to add symbol: AAPL. It may already exist or there was an error.");
    }

    @Test
    void processCommand_targetPriceAddFails_rollsBackSymbolAddition() {
        AddCommand command = new AddCommand("COHR", "Coherent Corp", 0.0, 0.0);
        when(stockSymbolRegistry.addSymbol("COHR", "Coherent Corp")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), anyString()))
                .thenReturn(false);

        addCommandProcessor.processCommand(command);

        verify(stockSymbolRegistry).addSymbol("COHR", "Coherent Corp");
        verify(stockSymbolRegistry).removeSymbol("COHR");
        verify(telegramClient).sendMessage("Failed to add symbol to target prices: COHR");
    }
}
