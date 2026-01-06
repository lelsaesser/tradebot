package org.tradelite.trading.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TransactionTest {

    @Test
    void createBuy_shouldInitializeBuyTransaction() {
        Transaction transaction = Transaction.createBuy("AAPL", 10.0, 150.0, "Buy signal");

        assertThat(transaction.getSymbol(), is("AAPL"));
        assertThat(transaction.getType(), is(Transaction.TransactionType.BUY));
        assertThat(transaction.getQuantity(), is(10.0));
        assertThat(transaction.getPrice(), is(150.0));
        assertThat(transaction.getTotalAmount(), is(1500.0));
        assertThat(transaction.getReason(), is("Buy signal"));
        assertNotNull(transaction.getTimestamp());
    }

    @Test
    void createSell_shouldInitializeSellTransaction() {
        Transaction transaction = Transaction.createSell("AAPL", 10.0, 160.0, "Sell signal");

        assertThat(transaction.getSymbol(), is("AAPL"));
        assertThat(transaction.getType(), is(Transaction.TransactionType.SELL));
        assertThat(transaction.getQuantity(), is(10.0));
        assertThat(transaction.getPrice(), is(160.0));
        assertThat(transaction.getTotalAmount(), is(1600.0));
        assertThat(transaction.getReason(), is("Sell signal"));
        assertNotNull(transaction.getTimestamp());
    }
}
