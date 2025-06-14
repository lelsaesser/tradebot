package org.tradelite.client.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddCommandProcessorTest {

    @Mock
    private TargetPriceProvider targetPriceProvider;
    @Mock
    private TelegramClient telegramClient;

    private AddCommandProcessor addCommandProcessor;

    @BeforeEach
    void setUp() {
        addCommandProcessor = new AddCommandProcessor(targetPriceProvider, telegramClient);
    }

    @Test
    void canProcess_addCommand_returnsTrue() {
        AddCommand command = new AddCommand(null, 0, 0, null);
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
        AddCommand command = new AddCommand(CoinId.BITCOIN, 50000.0, 60000.0, SymbolType.CRYPTO);
        when(targetPriceProvider.addSymbolToTargetPriceConfig(eq(command), anyString())).thenReturn(true);

        addCommandProcessor.processCommand(command);

        verify(targetPriceProvider).addSymbolToTargetPriceConfig(eq(command), anyString());
        verify(telegramClient).sendMessage("All set!\n" +
                "Added bitcoin with buy target 50000.0 and sell target 60000.0.");
    }

    @Test
    void processCommand_invalidAddCommand_sendsErrorMessage() {
        AddCommand command = new AddCommand(StockSymbol.AMZN, 0, 0, SymbolType.CRYPTO);

        when(targetPriceProvider.addSymbolToTargetPriceConfig(eq(command), anyString())).thenReturn(false);

        addCommandProcessor.processCommand(command);

        verify(telegramClient).sendMessage("Failed to add symbol: AMZN. It may already exist or there was an error.");
    }
}
