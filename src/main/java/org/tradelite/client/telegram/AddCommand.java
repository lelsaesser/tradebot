package org.tradelite.client.telegram;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AddCommand implements TelegramCommand {

    private String ticker;
    private String displayName;
    private double buyTargetPrice;
    private double sellTargetPrice;
}
