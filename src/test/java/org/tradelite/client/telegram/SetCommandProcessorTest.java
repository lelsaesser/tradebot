package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.StockSymbolRegistry;

@ExtendWith(MockitoExtension.class)
class SetCommandProcessorTest {

    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramClient telegramClient;
    @Mock private StockSymbolRegistry stockSymbolRegistry;

    private SetCommandProcessor setCommandProcessor;

    @BeforeEach
    void setUp() {
        setCommandProcessor =
                new SetCommandProcessor(targetPriceProvider, telegramClient, stockSymbolRegistry);
    }

    @Test
    void canProcess_returnsTrue_forSetCommand() {
        SetCommand command = new SetCommand("buy", "BITCOIN", 50000.0);
        boolean canProcess = setCommandProcessor.canProcess(command);

        assertThat(canProcess, is(true));
    }

    @Test
    void canProcess_returnsFalse_forNonSetCommand() {
        ShowCommand command = new ShowCommand("all");
        boolean canProcess = setCommandProcessor.canProcess(command);

        assertThat(canProcess, is(false));
    }

    @Test
    void processCommand_updatesTargetPrice_forValidCoin() {
        SetCommand command = new SetCommand("buy", "BITCOIN", 50000.0);

        setCommandProcessor.processCommand(command);

        verify(targetPriceProvider, times(1))
                .updateTargetPrice(
                        CoinId.fromString("BITCOIN").get(),
                        50000.0,
                        null,
                        TargetPriceProvider.FILE_PATH_COINS);
        verify(targetPriceProvider, never())
                .updateTargetPrice(any(StockSymbol.class), anyDouble(), anyDouble(), anyString());
    }

    @Test
    void processCommand_updatesTargetPrice_forValidStock() {
        SetCommand command = new SetCommand("sell", "AAPL", 150.0);
        StockSymbol aaplSymbol = new StockSymbol("AAPL", "Apple");
        when(stockSymbolRegistry.fromString("AAPL")).thenReturn(Optional.of(aaplSymbol));

        setCommandProcessor.processCommand(command);

        verify(targetPriceProvider, times(1))
                .updateTargetPrice(aaplSymbol, null, 150.0, TargetPriceProvider.FILE_PATH_STOCKS);
        verify(targetPriceProvider, never())
                .updateTargetPrice(any(CoinId.class), anyDouble(), anyDouble(), anyString());
    }

    @Test
    void processCommand_throwsException_forInvalidSymbol() {
        SetCommand command = new SetCommand("buy", "INVALID_SYMBOL", 100.0);
        when(stockSymbolRegistry.fromString("INVALID_SYMBOL")).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class, () -> setCommandProcessor.processCommand(command));

        verify(targetPriceProvider, never())
                .updateTargetPrice(any(), anyDouble(), anyDouble(), anyString());
    }

    @Test
    void processCommand_throwsException_forInvalidSubCommand() {
        SetCommand command = new SetCommand("invalid", "BITCOIN", 50000.0);

        assertThrows(
                IllegalArgumentException.class, () -> setCommandProcessor.processCommand(command));

        verify(targetPriceProvider, never())
                .updateTargetPrice(any(), anyDouble(), anyDouble(), anyString());
    }
}
