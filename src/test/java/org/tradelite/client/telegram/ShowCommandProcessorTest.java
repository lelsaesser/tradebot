package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

@ExtendWith(MockitoExtension.class)
class ShowCommandProcessorTest {

    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramGateway telegramClient;

    private ShowCommandProcessor showCommandProcessor;

    @BeforeEach
    void setUp() {
        showCommandProcessor = new ShowCommandProcessor(targetPriceProvider, telegramClient);
    }

    @Test
    void canProcess_showCommand_returnsTrue() {
        ShowCommand command = new ShowCommand(ShowCommandOptions.ALL.getName());

        boolean canProcess = showCommandProcessor.canProcess(command);

        assertThat(canProcess, is(true));
    }

    @Test
    void canProcess_nonShowCommand_returnsFalse() {
        SetCommand command = new SetCommand("buy", "BITCOIN", 50000.0);
        boolean canProcess = showCommandProcessor.canProcess(command);

        assertThat(canProcess, is(false));
    }

    @Test
    void processCommand_invalidCommand_sendsErrorMessage() {
        ShowCommand command = new ShowCommand("");

        showCommandProcessor.processCommand(command);

        verify(telegramClient, times(1))
                .sendMessage("Sub-command is required. Use all, coins, or stocks.");
        verify(targetPriceProvider, never()).getCoinTargetPrices();
        verify(targetPriceProvider, never()).getStockTargetPrices();
    }

    @ParameterizedTest
    @MethodSource("processCommandParamProvider")
    void processCommand_validCommand_subCommand_all_success(ShowCommand command) {
        when(targetPriceProvider.getCoinTargetPrices()).thenReturn(List.of());
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());

        showCommandProcessor.processCommand(command);

        verify(targetPriceProvider, times(1)).getCoinTargetPrices();
        verify(targetPriceProvider, times(1)).getStockTargetPrices();
        verify(telegramClient, times(1)).sendMessage(anyString());
    }

    private static Object[][] processCommandParamProvider() {
        return new Object[][] {
            {new ShowCommand(ShowCommandOptions.ALL.getName())},
            {new ShowCommand(ShowCommandOptions.COINS.getName())},
            {new ShowCommand(ShowCommandOptions.STOCKS.getName())}
        };
    }

    @Test
    void isValidCommand_validCommand_returnsTrue() {
        ShowCommand command = new ShowCommand(ShowCommandOptions.ALL.getName());

        boolean isValid = showCommandProcessor.isValidCommand(command);

        assertThat(isValid, is(true));
    }

    @ParameterizedTest
    @MethodSource("invalidCommandParamProvider")
    void isValidCommand_invalidCommand_returnsFalse(ShowCommand command) {
        boolean isValid = showCommandProcessor.isValidCommand(command);

        assertThat(isValid, is(false));
    }

    private static Object[][] invalidCommandParamProvider() {
        return new Object[][] {
            {new ShowCommand("invalid")}, {new ShowCommand("")}, {new ShowCommand(null)}
        };
    }

    @Test
    void builtResponseMessage_all() {
        List<TargetPrice> coinPrices =
                List.of(
                        new TargetPrice("BTC", 50000.0, 200000.0),
                        new TargetPrice("ETH", 3000.0, 10000.0));
        List<TargetPrice> stockPrices =
                List.of(
                        new TargetPrice("AAPL", 150.0, 300.0),
                        new TargetPrice("GOOG", 180.0, 500.0));

        String responseMessage = showCommandProcessor.builtResponseMessage(coinPrices, stockPrices);

        assertThat(responseMessage, containsStringIgnoringCase("stocks:"));
        assertThat(responseMessage, containsStringIgnoringCase("cryptos:"));
        assertThat(
                responseMessage,
                containsStringIgnoringCase(
                        "Current monitoring watchlist contains following symbols:"));
    }

    @Test
    void builtResponseMessage_coinsOnly() {
        List<TargetPrice> coinPrices = List.of(new TargetPrice("BTC", 50000.0, 200000.0));
        List<TargetPrice> stockPrices = List.of();

        String responseMessage = showCommandProcessor.builtResponseMessage(coinPrices, stockPrices);

        assertThat(responseMessage, containsStringIgnoringCase("cryptos:"));
        assertThat(responseMessage, not(containsStringIgnoringCase("stocks:")));
        assertThat(
                responseMessage,
                containsStringIgnoringCase(
                        "Current monitoring watchlist contains following symbols:"));
    }

    @Test
    void builtResponseMessage_stocksOnly() {
        List<TargetPrice> coinPrices = List.of();
        List<TargetPrice> stockPrices = List.of(new TargetPrice("AAPL", 150.0, 300.0));

        String responseMessage = showCommandProcessor.builtResponseMessage(coinPrices, stockPrices);

        assertThat(responseMessage, containsStringIgnoringCase("stocks:"));
        assertThat(responseMessage, not(containsStringIgnoringCase("cryptos:")));
        assertThat(
                responseMessage,
                containsStringIgnoringCase(
                        "Current monitoring watchlist contains following symbols:"));
    }
}
