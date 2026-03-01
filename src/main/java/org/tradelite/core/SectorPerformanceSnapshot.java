package org.tradelite.core;

import java.time.LocalDate;
import java.util.List;
import org.tradelite.client.finviz.dto.IndustryPerformance;

public record SectorPerformanceSnapshot(
        LocalDate fetchDate, List<IndustryPerformance> performances) {}
