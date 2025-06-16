package org.tradelite.client.telegram;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.tradelite.common.SymbolType;
import org.tradelite.common.TickerSymbol;

@Data
@AllArgsConstructor
public class RemoveCommand implements TelegramCommand {

    private TickerSymbol symbol;
    private SymbolType symbolType;
}
