package org.tradelite.web.dashboard.dto;

import org.tradelite.common.TargetSide;

public record SetTargetRequest(String ticker, TargetSide side, double price) {}
