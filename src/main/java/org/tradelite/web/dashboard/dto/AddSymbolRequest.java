package org.tradelite.web.dashboard.dto;

import jakarta.validation.constraints.NotBlank;

public record AddSymbolRequest(@NotBlank String ticker, @NotBlank String displayName) {}
