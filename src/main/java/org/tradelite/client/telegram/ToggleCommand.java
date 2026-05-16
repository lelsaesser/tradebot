package org.tradelite.client.telegram;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ToggleCommand implements TelegramCommand {

    /** Feature name (camelCase key). Null means "show all toggles". */
    private String featureName;

    /** True = on, False = off. Null when featureName is null (show-all mode). */
    private Boolean enabled;
}
