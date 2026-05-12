package org.tradelite.repository;

import java.util.List;

public interface ApiMeteringRepository {

    void saveAll(List<ApiMeteringRecord> records);

    List<ApiMeteringRecord> findByMonth(String month);
}
