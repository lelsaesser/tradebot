package org.tradelite.client.telegram;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TelegramCommandDispatcher {

    private final List<TelegramCommandProcessor<? extends TelegramCommand>> processors;

    public TelegramCommandDispatcher(List<TelegramCommandProcessor<? extends TelegramCommand>> processors) {
        this.processors = processors;
    }

    public void dispatch(TelegramCommand command) {
        for (TelegramCommandProcessor<? extends TelegramCommand> processor : processors) {
            if (processor.canProcess(command)) {
                invokeProcessor(processor, command);
                return;
            }
        }
        throw new IllegalArgumentException("No processor found for command: " + command.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private <T extends TelegramCommand> void invokeProcessor(TelegramCommandProcessor<? extends TelegramCommand> processor, TelegramCommand command) {
        ((TelegramCommandProcessor<T>) processor).processCommand((T) command);
    }
}
