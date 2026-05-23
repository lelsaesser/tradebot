package org.tradelite.web.dashboard;

import java.time.Instant;

public record DashboardEvent(String type, Instant timestamp, Object payload) {

    public static DashboardEvent of(String type, Object payload) {
        return new DashboardEvent(type, Instant.now(), payload);
    }
}
