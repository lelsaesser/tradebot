package org.tradelite.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.tradelite.core.SectorPerformanceSnapshot;

public interface SectorPerformanceRepository {

    void saveSnapshot(SectorPerformanceSnapshot snapshot);

    List<SectorPerformanceSnapshot> findSnapshotsSince(LocalDate since);

    Optional<SectorPerformanceSnapshot> findLatestSnapshot();

    Optional<SectorPerformanceSnapshot> findByDate(LocalDate date);
}
