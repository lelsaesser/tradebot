package org.tradelite.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQLite implementation of {@link InsiderTransactionRepository}.
 *
 * <p>Uses DELETE all + batch INSERT for the weekly full-replace pattern. This matches the semantics
 * of the previous JSON file approach where the entire file was overwritten each week.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteInsiderTransactionRepository implements InsiderTransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void saveAll(List<InsiderTransactionRow> rows) {
        jdbcTemplate.update("DELETE FROM insider_transactions");

        if (rows.isEmpty()) {
            log.debug("Cleared insider transactions (no new data)");
            return;
        }

        String sql =
                """
                INSERT INTO insider_transactions (symbol, transaction_type, count)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(
                sql,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NonNull PreparedStatement ps, int i)
                            throws SQLException {
                        InsiderTransactionRow row = rows.get(i);
                        ps.setString(1, row.symbol());
                        ps.setString(2, row.transactionType());
                        ps.setInt(3, row.count());
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                });

        log.debug("Saved {} insider transaction rows", rows.size());
    }

    @Override
    public List<InsiderTransactionRow> findAll() {
        String sql =
                """
                SELECT symbol, transaction_type, count
                FROM insider_transactions
                """;

        return jdbcTemplate.query(
                sql,
                (rs, _) ->
                        new InsiderTransactionRow(
                                rs.getString("symbol"),
                                rs.getString("transaction_type"),
                                rs.getInt("count")));
    }
}
