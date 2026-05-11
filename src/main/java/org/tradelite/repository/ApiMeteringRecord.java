package org.tradelite.repository;

import java.time.LocalDateTime;

public record ApiMeteringRecord(
        String provider, String month, int count, LocalDateTime lastUpdated) {}
