package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.dto.TelegramMessage;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.common.CoinId;
import org.tradelite.common.TickerSymbol;

@ExtendWith(MockitoExtension.class)
class TelegramMessageProcessorTest {

    @Mock private TelegramGateway telegramClient;
    @Mock private TelegramCommandDispatcher commandDispatcher;
    @Mock private TelegramMessageTracker messageTracker;
    @Mock private org.tradelite.service.StockSymbolRegistry stockSymbolRegistry;

    private TelegramMessageProcessor messageProcessor;

    @BeforeEach
    void setUp() {
        messageProcessor =
                new TelegramMessageProcessor(
                        telegramClient, commandDispatcher, messageTracker, stockSymbolRegistry);

        // Setup lenient mock responses for common stock symbols
        lenient()
                .when(stockSymbolRegistry.fromString("pltr"))
                .thenReturn(
                        java.util.Optional.of(
                                new org.tradelite.common.StockSymbol("PLTR", "Palantir")));
        lenient()
                .when(stockSymbolRegistry.fromString("aapl"))
                .thenReturn(
                        java.util.Optional.of(
                                new org.tradelite.common.StockSymbol("AAPL", "Apple")));
        lenient()
                .when(stockSymbolRegistry.fromString("invalid_symbol"))
                .thenReturn(java.util.Optional.empty());
        lenient()
                .when(
                        stockSymbolRegistry.fromString(
                                argThat(
                                        s ->
                                                s != null
                                                        && !s.equals("pltr")
                                                        && !s.equals("aapl")
                                                        && !s.equals("invalid_symbol"))))
                .thenReturn(java.util.Optional.empty());
    }

    @ParameterizedTest
    @MethodSource("validInputsProvider")
    void buildSetCommand_validInputs_createsSetCommand(
            String subCommand, String symbol, double target) {
        var command = messageProcessor.buildSetCommand(subCommand, symbol, target);
        assertThat(command.isPresent(), is(true));
        assertThat(command.get().getSubCommand(), is(subCommand));
        assertThat(command.get().getSymbol(), is(symbol.toLowerCase()));
        assertThat(command.get().getTarget(), is(target));
    }

    private static Stream<Arguments> validInputsProvider() {
        return Stream.of(
                Arguments.of("buy", "bitcoin", 50000.0),
                Arguments.of("sell", "solana", 1000.0),
                Arguments.of("buy", "pltr", 80.0),
                Arguments.of("sell", "aapl", 500.0),
                Arguments.of("buy", "aapl", 0),
                Arguments.of("sell", "aapl", 0));
    }

    @ParameterizedTest
    @MethodSource("invalidInputsProvider")
    void buildSetCommand_invalidInputs_returnsEmpty(
            String subCommand, String symbol, double target) {
        var command = messageProcessor.buildSetCommand(subCommand, symbol, target);
        assertThat(command.isPresent(), is(false));
    }

    private static Stream<Arguments> invalidInputsProvider() {
        return Stream.of(
                Arguments.of("buy", "bitcoin", -50000.0), // Negative target
                Arguments.of("buy", "pltr", -80.0), // Negative target for stock
                Arguments.of(null, "bitcoin", 50000.0), // Null subCommand
                Arguments.of("invalid", "bitcoin", 50000.0), // Invalid subCommand
                Arguments.of("buy", "", 50000.0), // Empty symbol
                Arguments.of("buy", "invalid_symbol", 50000.0) // Invalid symbol
                );
    }

    @ParameterizedTest
    @MethodSource("parseSetCommandValidInputsProvider")
    void parseMessage_validSetCommand_returnsSetCommand(String text) {
        TelegramMessage message = new TelegramMessage();
        message.setText(text);
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);
        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(SetCommand.class)));
    }

    private static Stream<Arguments> parseSetCommandValidInputsProvider() {
        return Stream.of(
                Arguments.of("/set buy bitcoin 50000.0"),
                Arguments.of("/SET sell solana 1000.0"),
                Arguments.of("/Set buy pltr 80.0"),
                Arguments.of("/sEt sell aapl 500.0"),
                Arguments.of("/seT buy aapl 0"),
                Arguments.of("/sET sell aapl 0"));
    }

    @ParameterizedTest
    @MethodSource("parseShowCommandValidInputsProvider")
    void parseMessage_validShowCommand_returnsShowCommand(String text) {
        TelegramMessage message = new TelegramMessage();
        message.setText(text);
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);
        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(ShowCommand.class)));
    }

    private static Stream<Arguments> parseShowCommandValidInputsProvider() {
        return Stream.of(
                Arguments.of("/show all"),
                Arguments.of("/show coins"),
                Arguments.of("/show stocks"),
                Arguments.of("/SHOW stocks"),
                Arguments.of("/Show stocks"),
                Arguments.of("/shOW stocks"));
    }

    @Test
    void parseMessage_validAddCommand_returnsAddCommand() {
        String text = "/add COHR Coherent_Corp";
        TelegramMessage message = new TelegramMessage();
        message.setText(text);
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(AddCommand.class)));
    }

    @Test
    void parseMessage_validRemoveCommand_returnsRemoveCommand() {
        String text = "/remove COHR";
        TelegramMessage message = new TelegramMessage();
        message.setText(text);
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(RemoveCommand.class)));
    }

    @Test
    void parseMessage_validRsiCommand_returnsRsiCommand() {
        String text = "/rsi bitcoin";
        TelegramMessage message = new TelegramMessage();
        message.setText(text);
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(RsiCommand.class)));
    }

    @ParameterizedTest
    @MethodSource("parseMessageInvalidInputsProvider")
    void parseMessage_invalidInputs_returnsEmpty(String text) {
        TelegramMessage message = new TelegramMessage();
        message.setText(text);
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);
        assertThat(command.isPresent(), is(false));
    }

    private static Stream<Arguments> parseMessageInvalidInputsProvider() {
        return Stream.of(
                Arguments.of("/set buy bitcoin -50000.0"), // Negative target
                Arguments.of("/set buy pltr -80.0"), // Negative target for stock
                Arguments.of("/set invalid_symbol 50000.0"), // Invalid symbol
                Arguments.of("set AAPL 50000.0"), // missing slash
                Arguments.of("invalid command format"), // Not a set command
                Arguments.of(""), // Not a set command
                Arguments.of("show all"), // missing slash
                Arguments.of("/show all bla bla"), // Not a valid show command
                Arguments.of("/show all bla"), // Not a valid show command
                Arguments.of("/remove bla bla") // Not a valid remove command
                );
    }

    @Test
    void processUpdates_validUpdate_processesCommand() {
        TelegramMessage message = new TelegramMessage();
        message.setText("/set buy bitcoin 50000.0");
        message.setMessageId(1L);

        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        messageProcessor.processUpdates(List.of(update));

        verify(commandDispatcher, times(1)).dispatch(any(SetCommand.class));
        verify(messageTracker, times(1)).setLastProcessedMessageId(1L);
    }

    @Test
    void processUpdates_alreadyProcessedMessage_skipsProcessing() {
        when(messageTracker.getLastProcessedMessageId()).thenReturn(1L);

        TelegramMessage message = new TelegramMessage();
        message.setText("/set buy bitcoin 50000.0");
        message.setMessageId(1L);

        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        messageProcessor.processUpdates(List.of(update));

        verify(messageTracker, times(1)).getLastProcessedMessageId();
        verify(commandDispatcher, never()).dispatch(any(SetCommand.class));
        verify(messageTracker, never()).setLastProcessedMessageId(1L);
    }

    @Test
    void processUpdates_nullMessageText_skipsProcessing() {
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(new TelegramMessage());

        messageProcessor.processUpdates(List.of(update));

        verify(commandDispatcher, never()).dispatch(any());
        verify(messageTracker, never()).setLastProcessedMessageId(anyLong());
    }

    @Test
    void processUpdates_nullMessage_skipsProcessing() {
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(null);

        messageProcessor.processUpdates(List.of(update));

        verify(commandDispatcher, never()).dispatch(any());
        verify(messageTracker, never()).setLastProcessedMessageId(anyLong());
    }

    @Test
    void parseAddCommand_validInput_returnsAddCommand() {
        String messageText = "/add COHR Coherent_Corp";
        Optional<AddCommand> command = messageProcessor.parseAddCommand(messageText);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get().getTicker(), is("COHR"));
        assertThat(command.get().getDisplayName(), is("Coherent Corp"));
        assertThat(command.get().getBuyTargetPrice(), is(0.0));
        assertThat(command.get().getSellTargetPrice(), is(0.0));
    }

    @ParameterizedTest
    @CsvSource({
        "/add COHR",
        "/add COHR Coherent_Corp Extra",
        "/add",
    })
    void parseAddCommand_invalidInputs_returnsEmpty(String commandText) {
        Optional<AddCommand> command = messageProcessor.parseAddCommand(commandText);

        assertThat(command.isPresent(), is(false));

        verify(commandDispatcher, never()).dispatch(any(AddCommand.class));
        verify(telegramClient, times(1)).sendMessage(anyString());
    }

    @ParameterizedTest
    @CsvSource({
        "/rsi",
        "/rsi bitcoin extra",
        "/rsi invalid_symbol",
    })
    void parseRsiCommand_invalidInputs_returnsEmpty(String commandText) {
        Optional<RsiCommand> command = messageProcessor.parseRsiCommand(commandText);

        assertThat(command.isPresent(), is(false));

        verify(commandDispatcher, never()).dispatch(any(RsiCommand.class));
        verify(telegramClient, times(1)).sendMessage(anyString());
    }

    @Test
    void parseTickerSymbol_validCoin_returnsCoinId() {
        String ticker = "bitcoin";
        Optional<TickerSymbol> coinId = messageProcessor.parseTickerSymbol(ticker);

        assertThat(coinId.isPresent(), is(true));
        assertThat(coinId.get().getName(), is("bitcoin"));
        assertThat(coinId.get(), instanceOf(CoinId.class));
    }

    @Test
    void parseTickerSymbol_invalidTicker_returnsEmpty() {
        String ticker = "invalid_ticker";
        Optional<TickerSymbol> result = messageProcessor.parseTickerSymbol(ticker);

        assertThat(result.isPresent(), is(false));
    }

    @Test
    void parseTickerSymbol_emptyTicker_returnsEmpty() {
        String ticker = "";
        Optional<TickerSymbol> result = messageProcessor.parseTickerSymbol(ticker);

        assertThat(result.isPresent(), is(false));
    }

    @Test
    void parseTickerSymbol_nullTicker_returnsEmpty() {
        Optional<TickerSymbol> result = messageProcessor.parseTickerSymbol(null);

        assertThat(result.isPresent(), is(false));
    }

    @Test
    void tryParseDouble_nullValue_returnsEmpty() {
        Optional<Double> result = messageProcessor.tryParseDouble(null);
        assertThat(result.isPresent(), is(false));
    }
}
