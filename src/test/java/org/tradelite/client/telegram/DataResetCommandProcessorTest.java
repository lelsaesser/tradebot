package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.repository.OhlcvRepository;
import org.tradelite.service.OhlcvFetcher;

@ExtendWith(MockitoExtension.class)
class DataResetCommandProcessorTest {

    @Mock private OhlcvRepository ohlcvRepository;
    @Mock private OhlcvFetcher ohlcvFetcher;
    @Mock private TelegramGateway telegramGateway;

    @InjectMocks private DataResetCommandProcessor processor;

    @Test
    void canProcess_dataResetCommand_returnsTrue() {
        assertThat(processor.canProcess(new DataResetCommand("AAPL")), is(true));
    }

    @Test
    void canProcess_otherCommand_returnsFalse() {
        assertThat(processor.canProcess(new RemoveCommand("AAPL")), is(false));
    }

    @Test
    void processCommand_success_deletesBackfillsAndSendsConfirmation() {
        when(ohlcvRepository.deleteBySymbol("NFLX")).thenReturn(245);
        when(ohlcvFetcher.backfillSymbol("NFLX")).thenReturn(400);

        processor.processCommand(new DataResetCommand("NFLX"));

        verify(ohlcvRepository).deleteBySymbol("NFLX");
        verify(ohlcvFetcher).backfillSymbol("NFLX");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramGateway).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message, containsString("Data reset complete for NFLX"));
        assertThat(message, containsString("Deleted 245 records"));
        assertThat(message, containsString("re-fetched 400 records"));
    }

    @Test
    void processCommand_backfillFails_sendsErrorMessage() {
        when(ohlcvRepository.deleteBySymbol("NFLX")).thenReturn(245);
        when(ohlcvFetcher.backfillSymbol("NFLX"))
                .thenThrow(new RuntimeException("API error"));

        processor.processCommand(new DataResetCommand("NFLX"));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramGateway).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message, containsString("Data reset failed for NFLX"));
        assertThat(message, containsString("API error"));
    }

    @Test
    void processCommand_uppercasesTicker() {
        when(ohlcvRepository.deleteBySymbol("NFLX")).thenReturn(0);
        when(ohlcvFetcher.backfillSymbol("NFLX")).thenReturn(400);

        processor.processCommand(new DataResetCommand("nflx"));

        verify(ohlcvRepository).deleteBySymbol("NFLX");
        verify(ohlcvFetcher).backfillSymbol("NFLX");
    }
}
