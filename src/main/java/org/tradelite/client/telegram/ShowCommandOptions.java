package org.tradelite.client.telegram;

import lombok.Getter;

@Getter
public enum ShowCommandOptions {
    COINS("coins"),
    STOCKS("stocks"),
    ALL("all");

    private final String name;

    ShowCommandOptions(String name) {
        this.name = name;
    }
}
