package org.tradelite.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.tradelite.service.model.MomentumRocData;

/**
 * SQLite implementation of {@link MomentumRocRepository}.
 *
 * <p>Stores momentum ROC state data (previous ROC values) for each symbol to enable crossover
 * detection between daily calculation cycles.
 */
@Slf4j
@Repository
public class SqliteMomentumRocRepository implements MomentumRocRepository {

    private final DataSource dataSource;

    @Autowired
    public SqliteMomentumRocRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        initializeSchema();
    }

    private void initializeSchema() {
        String createTableSql =
                """
                CREATE TABLE IF NOT EXISTS momentum_roc_state (
                    symbol TEXT PRIMARY KEY,
                    previous_roc10 REAL NOT NULL,
                    previous_roc20 REAL NOT NULL,
                    initialized INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """;

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            log.info("SQLite momentum_roc_state table initialized successfully");
        } catch (SQLException e) {
            log.error("Failed to initialize SQLite momentum_roc_state schema", e);
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }

    @Override
    public void save(String symbol, MomentumRocData data) {
        String sql =
                """
                INSERT OR REPLACE INTO momentum_roc_state
                (symbol, previous_roc10, previous_roc20, initialized, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        long timestamp = System.currentTimeMillis() / 1000;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setDouble(2, data.getPreviousRoc10());
            pstmt.setDouble(3, data.getPreviousRoc20());
            pstmt.setInt(4, data.isInitialized() ? 1 : 0);
            pstmt.setLong(5, timestamp);
            pstmt.executeUpdate();
            log.debug("Saved momentum ROC state for {}", symbol);
        } catch (SQLException e) {
            log.error("Failed to save momentum ROC state for {}", symbol, e);
            throw new IllegalStateException("Failed to save momentum ROC state", e);
        }
    }

    @Override
    public Optional<MomentumRocData> findBySymbol(String symbol) {
        String sql =
                """
                SELECT previous_roc10, previous_roc20, initialized
                FROM momentum_roc_state
                WHERE symbol = ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    MomentumRocData data = new MomentumRocData();
                    data.setPreviousRoc10(rs.getDouble("previous_roc10"));
                    data.setPreviousRoc20(rs.getDouble("previous_roc20"));
                    data.setInitialized(rs.getInt("initialized") == 1);
                    return Optional.of(data);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find momentum ROC state for {}", symbol, e);
            throw new IllegalStateException("Failed to find momentum ROC state", e);
        }
        return Optional.empty();
    }
}
