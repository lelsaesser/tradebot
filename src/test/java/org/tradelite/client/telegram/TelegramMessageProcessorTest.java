package org.tradelite.client.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
class TelegramMessageProcessorTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private TelegramCommandDispatcher commandDispatcher;
    @Mock
    private TelegramMessageTracker messageTracker;

    private TelegramMessageProcessor messageProcessor;

    @BeforeEach
    void setUp() {
        messageProcessor = new TelegramMessageProcessor(telegramClient, commandDispatcher, messageTracker);
    }

    @ParameterizedTest
    @MethodSource("validInputsProvider")
    void buildSetCommand_validInputs_createsSetCommand(String subCommand, String symbol, double target) {
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
                Arguments.of("sell", "aapl", 500.0)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidInputsProvider")
    void buildSetCommand_invalidInputs_returnsEmpty(String subCommand, String symbol, double target) {
        var command = messageProcessor.buildSetCommand(subCommand, symbol, target);
        assertThat(command.isPresent(), is(false));
    }

    private static Stream<Arguments> invalidInputsProvider() {
        return Stream.of(
                Arguments.of("buy", "bitcoin", -50000.0), // Negative target
                Arguments.of("sell", "solana", 0.0), // Zero target
                Arguments.of("buy", "pltr", -80.0), // Negative target for stock
                Arguments.of("sell", "aapl", 0.0), // Zero target for stock
                Arguments.of(null, "bitcoin", 50000.0), // Null subCommand
                Arguments.of("invalid", "bitcoin", 50000.0), // Invalid subCommand
                Arguments.of("buy", "", 50000.0), // Empty symbol
                Arguments.of("buy", "invalid_symbol", 50000.0) // Invalid symbol
        );
    }
}
