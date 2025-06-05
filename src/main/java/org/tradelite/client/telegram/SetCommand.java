package org.tradelite.client.telegram;

import lombok.Data;

@Data
public class SetCommand implements TelegramCommand {

    private final String subCommand;
    private final String symbol;
    private final double target;
}
