package org.tradelite.client.finviz.dto;

import java.math.BigDecimal;

public record IndustryPerformance(
        String name,
        BigDecimal perfWeek,
        BigDecimal perfMonth,
        BigDecimal perfQuarter,
        BigDecimal perfHalf,
        BigDecimal perfYear,
        BigDecimal perfYtd,
        BigDecimal change) {}
