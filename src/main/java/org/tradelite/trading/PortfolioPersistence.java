package org.tradelite.trading;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.trading.model.Portfolio;
import org.tradelite.trading.model.Transaction;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioPersistence {

    private static final String PORTFOLIO_FILE = "config/demo-portfolio.json";
    private static final String TRANSACTIONS_FILE = "config/demo-transactions.json";
    private static final double INITIAL_BALANCE = 100000.0;

    private final ObjectMapper objectMapper;

    public Portfolio loadPortfolio() {
        File file = new File(PORTFOLIO_FILE);
        if (!file.exists()) {
            log.info(
                    "Portfolio file not found, creating initial portfolio with balance: {}",
                    INITIAL_BALANCE);
            Portfolio initialPortfolio = Portfolio.createInitial(INITIAL_BALANCE);
            savePortfolio(initialPortfolio);
            return initialPortfolio;
        }

        try {
            Portfolio portfolio = objectMapper.readValue(file, Portfolio.class);
            log.info("Loaded portfolio with cash balance: {}", portfolio.getCashBalance());
            return portfolio;
        } catch (IOException e) {
            log.error("Failed to load portfolio, creating new one", e);
            Portfolio initialPortfolio = Portfolio.createInitial(INITIAL_BALANCE);
            savePortfolio(initialPortfolio);
            return initialPortfolio;
        }
    }

    public void savePortfolio(Portfolio portfolio) {
        File file = new File(PORTFOLIO_FILE);
        try {
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, portfolio);
            log.debug("Portfolio saved successfully");
        } catch (IOException e) {
            log.error("Failed to save portfolio", e);
        }
    }

    public List<Transaction> loadTransactions() {
        File file = new File(TRANSACTIONS_FILE);
        if (!file.exists()) {
            log.info("Transactions file not found, returning empty list");
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    file,
                    objectMapper
                            .getTypeFactory()
                            .constructCollectionType(List.class, Transaction.class));
        } catch (IOException e) {
            log.error("Failed to load transactions, returning empty list", e);
            return new ArrayList<>();
        }
    }

    public void saveTransactions(List<Transaction> transactions) {
        File file = new File(TRANSACTIONS_FILE);
        try {
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, transactions);
            log.debug("Transactions saved successfully");
        } catch (IOException e) {
            log.error("Failed to save transactions", e);
        }
    }

    public void addTransaction(Transaction transaction) {
        List<Transaction> transactions = loadTransactions();
        transactions.add(transaction);
        saveTransactions(transactions);
    }
}
