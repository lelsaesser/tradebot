package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.tradelite.client.telegram.dto.TelegramChat;
import org.tradelite.client.telegram.dto.TelegramMessage;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TickerSymbol;
import org.tradelite.config.TradebotTelegramProperties;

@ExtendWith(MockitoExtension.class)
// Sender-validation tests share the TelegramMessageProcessor logger via ListAppender; serialize
// within this class so concurrent tests don't leak log events into each other's appenders.
@Execution(ExecutionMode.SAME_THREAD)
class TelegramMessageProcessorTest {

    private static final long ALLOWED_CHAT_ID = -1001234567890L;

    @Mock private TelegramGateway telegramClient;
    @Mock private TelegramCommandDispatcher commandDispatcher;
    @Mock private TelegramMessageTracker messageTracker;
    @Mock private SymbolRegistry symbolRegistry;

    private TelegramMessageProcessor messageProcessor;
    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        TradebotTelegramProperties properties = new TradebotTelegramProperties();
        properties.setGroupChatId(String.valueOf(ALLOWED_CHAT_ID));

        messageProcessor =
                new TelegramMessageProcessor(
                        telegramClient,
                        commandDispatcher,
                        messageTracker,
                        symbolRegistry,
                        properties);

        logger = (Logger) LoggerFactory.getLogger(TelegramMessageProcessor.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        // Setup lenient mock responses for common stock symbols
        lenient()
                .when(symbolRegistry.fromString("pltr"))
                .thenReturn(Optional.of(new StockSymbol("PLTR", "Palantir")));
        lenient()
                .when(symbolRegistry.fromString("aapl"))
                .thenReturn(Optional.of(new StockSymbol("AAPL", "Apple")));
        lenient().when(symbolRegistry.fromString("invalid_symbol")).thenReturn(Optional.empty());
        lenient()
                .when(
                        symbolRegistry.fromString(
                                argThat(
                                        s ->
                                                s != null
                                                        && !s.equals("pltr")
                                                        && !s.equals("aapl")
                                                        && !s.equals("invalid_symbol"))))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        if (logger != null && logAppender != null) {
            logger.detachAppender(logAppender);
        }
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
        messageProcessor.processUpdates(
                List.of(buildUpdateForProcess("/set buy bitcoin 50000.0", 1L, ALLOWED_CHAT_ID)));

        verify(commandDispatcher, times(1)).dispatch(any(SetCommand.class));
        verify(messageTracker, times(1)).setLastProcessedMessageId(1L);
    }

    @Test
    void processUpdates_alreadyProcessedMessage_skipsProcessing() {
        when(messageTracker.getLastProcessedMessageId()).thenReturn(1L);

        messageProcessor.processUpdates(
                List.of(buildUpdateForProcess("/set buy bitcoin 50000.0", 1L, ALLOWED_CHAT_ID)));

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
    void parseMessage_showCommand_caseInsensitiveSubCommand() {
        TelegramMessage message = new TelegramMessage();
        message.setText("/show ALL");
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(ShowCommand.class)));
        assertThat(((ShowCommand) command.get()).getSubCommand(), is("ALL"));
    }

    @Test
    void parseMessage_showCommand_mixedCaseSubCommand() {
        TelegramMessage message = new TelegramMessage();
        message.setText("/show Coins");
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(ShowCommand.class)));
        assertThat(((ShowCommand) command.get()).getSubCommand(), is("Coins"));
    }

    @Test
    void parseMessage_setCommand_caseInsensitiveSubCommand() {
        TelegramMessage message = new TelegramMessage();
        message.setText("/set BUY bitcoin 50000.0");
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(SetCommand.class)));
        assertThat(((SetCommand) command.get()).getSubCommand(), is("BUY"));
    }

    @Test
    void parseMessage_setCommand_mixedCaseSubCommand() {
        TelegramMessage message = new TelegramMessage();
        message.setText("/set Sell aapl 300.0");
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(SetCommand.class)));
        assertThat(((SetCommand) command.get()).getSubCommand(), is("Sell"));
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
    void parseMessage_validDataResetCommand_returnsDataResetCommand() {
        TelegramMessage message = new TelegramMessage();
        message.setText("/data reset AAPL");
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(DataResetCommand.class)));
        assertThat(((DataResetCommand) command.get()).getTicker(), is("AAPL"));
    }

    @Test
    void parseMessage_dataResetCommand_caseInsensitive() {
        TelegramMessage message = new TelegramMessage();
        message.setText("/DATA Reset nflx");
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(DataResetCommand.class)));
        assertThat(((DataResetCommand) command.get()).getTicker(), is("NFLX"));
    }

    @ParameterizedTest
    @CsvSource({
        "/data reset",
        "/data delete AAPL",
        "/data reset AAPL extra",
        "/data",
    })
    void parseDataResetCommand_invalidFormats_returnsEmpty(String commandText) {
        Optional<DataResetCommand> command = messageProcessor.parseDataResetCommand(commandText);

        assertThat(command.isPresent(), is(false));
        verify(telegramClient, times(1)).sendMessage(anyString());
    }

    @ParameterizedTest
    @CsvSource({
        "/set",
        "/set buy",
        "/set buy AAPL",
        "/set buy AAPL abc",
    })
    void parseSetCommand_malformedShape_sendsErrorAndReturnsEmpty(String commandText) {
        var command = messageProcessor.parseMessage(buildUpdate(commandText));

        assertThat(command.isPresent(), is(false));
        verify(telegramClient, times(1)).sendMessage(anyString());
    }

    @Test
    void parseToggleCommand_noArgs_returnsShowAllCommand() {
        Optional<ToggleCommand> command = messageProcessor.parseToggleCommand("/toggle");

        assertThat(command.isPresent(), is(true));
        assertThat(command.get().getFeatureName(), is((String) null));
        assertThat(command.get().getEnabled(), is((Boolean) null));
    }

    @Test
    void parseToggleCommand_featureOn_returnsEnableCommand() {
        Optional<ToggleCommand> command =
                messageProcessor.parseToggleCommand("/toggle emaReport on");

        assertThat(command.isPresent(), is(true));
        assertThat(command.get().getFeatureName(), is("emaReport"));
        assertThat(command.get().getEnabled(), is(true));
    }

    @Test
    void parseToggleCommand_featureOff_returnsDisableCommand() {
        Optional<ToggleCommand> command =
                messageProcessor.parseToggleCommand("/toggle vfiReport off");

        assertThat(command.isPresent(), is(true));
        assertThat(command.get().getFeatureName(), is("vfiReport"));
        assertThat(command.get().getEnabled(), is(false));
    }

    @ParameterizedTest
    @CsvSource({
        "/toggle emaReport",
        "/toggle emaReport yes",
        "/toggle emaReport on extra",
    })
    void parseToggleCommand_invalidFormats_returnsEmpty(String commandText) {
        Optional<ToggleCommand> command = messageProcessor.parseToggleCommand(commandText);

        assertThat(command.isPresent(), is(false));
        verify(telegramClient, times(1)).sendMessage(anyString());
    }

    /**
     * Regression for #456: the invalid-format error message contained {@code <feature_name>} and
     * {@code <on|off>}, whose underscore made Telegram's Markdown parser fail with "Can't find end
     * of the entity starting at byte offset 44". Underscore-bearing placeholders must be wrapped in
     * backticks so {@code parse_mode=Markdown} treats them as inline code.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "/toggle list",
                "/toggle emaReport yes",
                "/toggle emaReport on extra",
                "/add foo",
                "/add foo bar baz",
                "/remove",
                "/remove foo bar",
                "/rsi",
                "/rsi foo bar",
                "/data reset",
                "/data reset foo bar",
                "/set foo bar 1.0",
                "/set",
                "/set buy",
                "/set buy AAPL",
                "/set buy AAPL abc",
            })
    void parseInvalidCommand_errorMessageHasNoUnescapedMarkdownSpecials(String commandText) {
        messageProcessor.parseMessage(buildUpdate(commandText));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, atLeastOnce()).sendMessage(captor.capture());

        for (String msg : captor.getAllValues()) {
            // Strip backtick-delimited spans (inline code is escaped by Telegram's Markdown
            // parser),
            // then assert the remaining text contains no Markdown specials that would break
            // parsing.
            String outsideCode = msg.replaceAll("`[^`]*`", "");
            assertThat(
                    "Markdown specials outside backtick spans in: " + msg,
                    outsideCode,
                    not(anyOf(containsString("_"), containsString("*"), containsString("["))));
        }
    }

    private TelegramUpdateResponse buildUpdate(String text) {
        TelegramMessage message = new TelegramMessage();
        message.setText(text);
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);
        return update;
    }

    /**
     * Build an update suitable for {@link TelegramMessageProcessor#processUpdates} — sets text,
     * messageId, and chat ID so the new auth gate ({@code #465}) is reachable.
     */
    private TelegramUpdateResponse buildUpdateForProcess(String text, long messageId, long chatId) {
        TelegramChat chat = new TelegramChat();
        chat.setChatId(chatId);
        TelegramMessage message = new TelegramMessage();
        message.setText(text);
        message.setMessageId(messageId);
        message.setChat(chat);
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);
        return update;
    }

    @Test
    void parseMessage_validToggleCommand_returnsToggleCommand() {
        TelegramMessage message = new TelegramMessage();
        message.setText("/toggle emaReport on");
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(ToggleCommand.class)));
    }

    @Test
    void parseMessage_toggleCommandNoArgs_returnsToggleCommand() {
        TelegramMessage message = new TelegramMessage();
        message.setText("/toggle");
        TelegramUpdateResponse update = new TelegramUpdateResponse();
        update.setMessage(message);

        var command = messageProcessor.parseMessage(update);

        assertThat(command.isPresent(), is(true));
        assertThat(command.get(), is(instanceOf(ToggleCommand.class)));
    }

    // --- Sender validation tests (#465) ---

    @Test
    void processUpdates_unauthorizedChat_rejectsAndAdvancesWatermark() {
        long unauthorizedChatId = 99999L;
        TelegramUpdateResponse update =
                buildUpdateForProcess("/data reset AAPL", 7L, unauthorizedChatId);

        messageProcessor.processUpdates(List.of(update));

        verify(commandDispatcher, never()).dispatch(any());
        verify(telegramClient, never()).sendMessage(anyString());
        verify(messageTracker, times(1)).setLastProcessedMessageId(7L);
    }

    @Test
    void processUpdates_authorizedChat_dispatchesNormally() {
        TelegramUpdateResponse update = buildUpdateForProcess("/show all", 2L, ALLOWED_CHAT_ID);

        messageProcessor.processUpdates(List.of(update));

        verify(commandDispatcher, times(1)).dispatch(any(ShowCommand.class));
        verify(messageTracker, times(1)).setLastProcessedMessageId(2L);
    }

    @Test
    void processUpdates_unauthorizedChat_logsCommandPrefixOnly() {
        long unauthorizedChatId = 99999L;
        TelegramUpdateResponse update =
                buildUpdateForProcess("/set buy bitcoin 50000.0", 3L, unauthorizedChatId);

        messageProcessor.processUpdates(List.of(update));

        Optional<ILoggingEvent> warn =
                logAppender.list.stream()
                        .filter(e -> e.getLevel() == Level.WARN)
                        .filter(e -> e.getFormattedMessage().contains("Rejected command"))
                        .findFirst();
        assertThat(warn.isPresent(), is(true));
        String msg = warn.get().getFormattedMessage();
        assertThat(msg, containsString("prefix=/set"));
        // The full payload must not leak into the log line — it's attacker-controlled.
        assertThat(msg, not(containsString("buy")));
        assertThat(msg, not(containsString("bitcoin")));
        assertThat(msg, not(containsString("50000")));
    }

    @Test
    void processUpdates_oldUnauthorizedMessageId_skippedAsAlreadyProcessed() {
        when(messageTracker.getLastProcessedMessageId()).thenReturn(10L);

        long unauthorizedChatId = 99999L;
        // messageId == 10 → not greater than watermark → dedup gate trips first.
        TelegramUpdateResponse update =
                buildUpdateForProcess("/data reset AAPL", 10L, unauthorizedChatId);

        messageProcessor.processUpdates(List.of(update));

        boolean anyRejection =
                logAppender.list.stream()
                        .filter(e -> e.getLevel() == Level.WARN)
                        .anyMatch(e -> e.getFormattedMessage().contains("Rejected command"));
        assertThat(
                "Rejection WARN must not fire for already-processed messages",
                anyRejection,
                is(false));
        verify(commandDispatcher, never()).dispatch(any());
        verify(messageTracker, never()).setLastProcessedMessageId(anyLong());
    }

    @Test
    void constructor_malformedGroupChatId_throwsNumberFormatException() {
        TradebotTelegramProperties badProps = new TradebotTelegramProperties();
        badProps.setGroupChatId("not-a-number");

        org.junit.jupiter.api.Assertions.assertThrows(
                NumberFormatException.class,
                () ->
                        new TelegramMessageProcessor(
                                telegramClient,
                                commandDispatcher,
                                messageTracker,
                                symbolRegistry,
                                badProps));
    }
}
