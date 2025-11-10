package org.tradelite.client.telegram;

import lombok.Data;
import org.tradelite.common.TickerSymbol;

@Data
public class RsiCommand implements TelegramCommand {
    private final TickerSymbol symbol;
}
