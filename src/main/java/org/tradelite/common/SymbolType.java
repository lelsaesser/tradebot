package org.tradelite.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SymbolType {
    STOCK("STOCK"),
    CRYPTO("CRYPTO");

    private final String type;
}
