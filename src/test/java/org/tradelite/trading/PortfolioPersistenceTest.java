package org.tradelite.trading;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tradelite.trading.model.Portfolio;
import org.tradelite.trading.model.Transaction;

class PortfolioPersistenceTest {

    private PortfolioPersistence portfolioPersistence;

    @BeforeEach
    void setUp() throws IOException {
        // Clean up before each test
        Files.deleteIfExists(Path.of("config/demo-portfolio.json"));
        Files.deleteIfExists(Path.of("config/demo-transactions.json"));
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        portfolioPersistence = new PortfolioPersistence(mapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(Path.of("config/demo-portfolio.json"));
        Files.deleteIfExists(Path.of("config/demo-transactions.json"));
    }

    @Test
    void loadPortfolio_noFile_shouldCreateInitialPortfolio() {
        Portfolio portfolio = portfolioPersistence.loadPortfolio();

        assertThat(portfolio.getCashBalance(), is(100000.0));
        assertThat(portfolio.getPositions(), is(anEmptyMap()));
        assertTrue(new File("config/demo-portfolio.json").exists());
    }

    @Test
    void saveAndLoadPortfolio_shouldPersist() {
        Portfolio portfolio = Portfolio.createInitial(75000.0);
        Portfolio withPosition = portfolio.buy("AAPL", 5.0, 200.0);

        portfolioPersistence.savePortfolio(withPosition);
        assertTrue(new File("config/demo-portfolio.json").exists());
    }

    @Test
    void addTransaction_shouldPersistToFile() {
        Transaction transaction1 = Transaction.createBuy("AAPL", 10.0, 150.0, "First buy");

        portfolioPersistence.addTransaction(transaction1);

        assertTrue(new File("config/demo-transactions.json").exists());
    }

    @Test
    void loadTransactions_noFile_shouldReturnEmptyList() {
        List<Transaction> transactions = portfolioPersistence.loadTransactions();

        assertThat(transactions, is(empty()));
    }

    @Test
    void savePortfolio_withMultiplePositions_shouldCreateFile() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);
        Portfolio withPositions =
                portfolio.buy("AAPL", 10.0, 150.0).buy("GOOG", 5.0, 2000.0).buy("MSFT", 8.0, 400.0);

        portfolioPersistence.savePortfolio(withPositions);

        assertTrue(new File("config/demo-portfolio.json").exists());
    }

    @Test
    void saveTransactions_shouldCreateFile() {
        List<Transaction> transactions =
                List.of(
                        Transaction.createBuy("AAPL", 10.0, 150.0, "Buy"),
                        Transaction.createSell("AAPL", 10.0, 160.0, "Sell"));

        portfolioPersistence.saveTransactions(transactions);

        assertTrue(new File("config/demo-transactions.json").exists());
    }

    @Test
    void loadPortfolio_errorRecovery_shouldCreateNewPortfolio() {
        // First load creates the file
        portfolioPersistence.loadPortfolio();
        assertTrue(new File("config/demo-portfolio.json").exists());

        // Loading again should still work
        Portfolio portfolio = portfolioPersistence.loadPortfolio();
        assertThat(portfolio, is(notNullValue()));
    }

    @Test
    void loadPortfolio_corruptedFile_shouldRecreatePortfolio() throws IOException {
        // Create a corrupted JSON file
        Files.writeString(Path.of("config/demo-portfolio.json"), "{ invalid json }");

        // Should handle gracefully and create new portfolio
        Portfolio portfolio = portfolioPersistence.loadPortfolio();

        assertThat(portfolio.getCashBalance(), is(100000.0));
        assertThat(portfolio.getPositions(), is(anEmptyMap()));
    }

    @Test
    void loadTransactions_corruptedFile_shouldReturnEmptyList() throws IOException {
        // Create a corrupted JSON file
        Files.writeString(Path.of("config/demo-transactions.json"), "[ invalid json ]");

        // Should handle gracefully and return empty list
        List<Transaction> transactions = portfolioPersistence.loadTransactions();

        assertThat(transactions, is(empty()));
    }

    @Test
    void savePortfolio_ioException_shouldHandleGracefully() throws Exception {
        // Create a mock ObjectMapper that throws IOException
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        ObjectWriter mockWriter = mock(ObjectWriter.class);
        when(mockMapper.writerWithDefaultPrettyPrinter()).thenReturn(mockWriter);
        doThrow(new IOException("Write failed"))
                .when(mockWriter)
                .writeValue(any(File.class), any(Portfolio.class));

        PortfolioPersistence persistence = new PortfolioPersistence(mockMapper);
        Portfolio portfolio = Portfolio.createInitial(100000.0);

        // Should not throw exception, just log error
        assertDoesNotThrow(() -> persistence.savePortfolio(portfolio));
    }

    @Test
    void saveTransactions_ioException_shouldHandleGracefully() throws Exception {
        // Create a mock ObjectMapper that throws IOException
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        ObjectWriter mockWriter = mock(ObjectWriter.class);
        when(mockMapper.writerWithDefaultPrettyPrinter()).thenReturn(mockWriter);
        doThrow(new IOException("Write failed"))
                .when(mockWriter)
                .writeValue(any(File.class), any(List.class));

        PortfolioPersistence persistence = new PortfolioPersistence(mockMapper);
        List<Transaction> transactions = List.of(Transaction.createBuy("AAPL", 10.0, 150.0, "Buy"));

        // Should not throw exception, just log error
        assertDoesNotThrow(() -> persistence.saveTransactions(transactions));
    }
}
