package org.tradelite.client.telegram;

public interface TelegramCommandProcessor<T extends TelegramCommand> {

    boolean canProcess(TelegramCommand command);

    void processCommand(T command);
}
