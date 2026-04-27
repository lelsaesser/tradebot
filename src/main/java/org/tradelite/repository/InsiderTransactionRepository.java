package org.tradelite.repository;

import java.util.List;

/**
 * Repository interface for persisting insider transaction counts.
 *
 * <p>Stores the latest weekly insider transaction counts per symbol. The entire dataset is replaced
 * each week when a new insider trading report is generated.
 */
public interface InsiderTransactionRepository {

    /**
     * Replaces all insider transaction data with the provided rows. Deletes all existing rows
     * first, then inserts the new data.
     *
     * @param rows The new transaction count rows to persist
     */
    void saveAll(List<InsiderTransactionRow> rows);

    /**
     * Returns all persisted insider transaction rows.
     *
     * @return List of all transaction count rows
     */
    List<InsiderTransactionRow> findAll();

    /**
     * A single row in the insider_transactions table.
     *
     * @param symbol The stock ticker symbol
     * @param transactionType The transaction type code (e.g., "P", "S")
     * @param count The transaction count
     */
    record InsiderTransactionRow(String symbol, String transactionType, int count) {}
}
