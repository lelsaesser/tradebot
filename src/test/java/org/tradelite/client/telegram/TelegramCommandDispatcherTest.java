package org.tradelite.client.telegram;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramCommandDispatcherTest {

    @Mock ShowCommandProcessor showCommandProcessor;
    @Mock SetCommandProcessor setCommandProcessor;

    private TelegramCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        List<TelegramCommandProcessor<? extends TelegramCommand>> processors =
                List.of(showCommandProcessor, setCommandProcessor);
        dispatcher = new TelegramCommandDispatcher(processors);
    }

    @Test
    void dispatch_setCommand_success() {
        when(setCommandProcessor.canProcess(any(SetCommand.class))).thenReturn(true);
        when(showCommandProcessor.canProcess(any(SetCommand.class))).thenReturn(false);

        SetCommand command = new SetCommand("buy", "BITCOIN", 50000.0);

        dispatcher.dispatch(command);

        verify(setCommandProcessor, times(1)).processCommand(command);
        verify(showCommandProcessor, never()).processCommand(any());
    }

    @Test
    void dispatch_showCommand_success() {
        when(showCommandProcessor.canProcess(any(ShowCommand.class))).thenReturn(true);

        ShowCommand command = new ShowCommand("all");

        dispatcher.dispatch(command);

        verify(showCommandProcessor, times(1)).processCommand(command);
        verify(setCommandProcessor, never()).processCommand(any());
    }

    @Test
    void dispatch_noProcessorFound_throwsException() {
        TelegramCommand unknownCommand = new TelegramCommand() {};

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(unknownCommand));

        verify(setCommandProcessor, never()).processCommand(any());
        verify(showCommandProcessor, never()).processCommand(any());
    }
}
