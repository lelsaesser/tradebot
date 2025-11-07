package org.tradelite.client.telegram;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.RsiService;

@ExtendWith(MockitoExtension.class)
public class RsiCommandProcessorTest {

    @Mock private RsiService rsiService;

    @Mock private TelegramClient telegramClient;

    @InjectMocks private RsiCommandProcessor processor;

    @Test
    public void shouldSendMessageWithRsiValueWhenFound() {
        RsiCommand command = new RsiCommand(StockSymbol.AAPL);
        when(rsiService.getCurrentRsi(StockSymbol.AAPL)).thenReturn(Optional.of(60.5));

        processor.processCommand(command);

        verify(telegramClient).sendMessage("RSI for AAPL is 60.50");
    }

    @Test
    public void shouldSendMessageWithNotEnoughDataWhenRsiNotFound() {
        RsiCommand command = new RsiCommand(StockSymbol.AAPL);
        when(rsiService.getCurrentRsi(StockSymbol.AAPL)).thenReturn(Optional.empty());

        processor.processCommand(command);

        verify(telegramClient).sendMessage("Not enough data to calculate RSI for AAPL");
    }
}
