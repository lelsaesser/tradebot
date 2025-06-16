package org.tradelite.client.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolType;
import org.tradelite.common.TargetPriceProvider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveCommandProcessorTest {

    @Mock
    private TargetPriceProvider targetPriceProvider;

    private RemoveCommandProcessor removeCommandProcessor;

    @BeforeEach
    void setUp() {
        removeCommandProcessor = new RemoveCommandProcessor(targetPriceProvider);
    }

    @Test
    void canProcess_removeCommand_returnsTrue() {
        RemoveCommand command = new RemoveCommand(StockSymbol.PLTR, SymbolType.STOCK);

        boolean canProcess = removeCommandProcessor.canProcess(command);

        assertThat(canProcess, is(true));
    }

    @Test
    void canProcess_addCommand_returnsFalse() {
        AddCommand command = new AddCommand(StockSymbol.PLTR, 10.0, 20.0, SymbolType.STOCK);

        boolean canProcess = removeCommandProcessor.canProcess(command);

        assertThat(canProcess, is(false));
    }

    @Test
    void processCommand_stock() {
        RemoveCommand command = new RemoveCommand(StockSymbol.PLTR, SymbolType.STOCK);

        removeCommandProcessor.processCommand(command);

        verify(targetPriceProvider, times(1)).removeSymbolFromTargetPriceConfig(command, TargetPriceProvider.FILE_PATH_STOCKS);
        verify(targetPriceProvider, never()).removeSymbolFromTargetPriceConfig(any(), eq(TargetPriceProvider.FILE_PATH_COINS));
    }

    @Test
    void processCommand_coin() {
        RemoveCommand command = new RemoveCommand(CoinId.BITCOIN, SymbolType.CRYPTO);

        removeCommandProcessor.processCommand(command);

        verify(targetPriceProvider, times(1)).removeSymbolFromTargetPriceConfig(command, TargetPriceProvider.FILE_PATH_COINS);
        verify(targetPriceProvider, never()).removeSymbolFromTargetPriceConfig(any(), eq(TargetPriceProvider.FILE_PATH_STOCKS));
    }
}
