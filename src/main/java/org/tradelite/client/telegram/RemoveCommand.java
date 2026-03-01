package org.tradelite.client.telegram;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RemoveCommand implements TelegramCommand {

    private String ticker;
}
