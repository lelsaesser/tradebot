package org.tradelite.web.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.tradelite.common.TargetSide;

public record SetTargetRequest(
        @NotBlank String ticker, @NotNull TargetSide side, @PositiveOrZero double price) {}
