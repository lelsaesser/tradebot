package org.tradelite.client.telegram;

public class ShowCommandProcessor implements TelegramCommandProcessor<ShowCommand>{

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof ShowCommand;
    }

    @Override
    public void processCommand(ShowCommand command) {
        // This is a placeholder for the ShowCommand processing logic.
    }
}
