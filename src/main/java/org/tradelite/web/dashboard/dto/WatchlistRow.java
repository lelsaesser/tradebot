package org.tradelite.web.dashboard.dto;

public record WatchlistRow(
        String ticker,
        String displayName,
        String exchange,
        Double currentPrice,
        Double buyTarget,
        Double sellTarget) {}
