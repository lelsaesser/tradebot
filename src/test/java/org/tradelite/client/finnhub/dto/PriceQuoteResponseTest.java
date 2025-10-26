package org.tradelite.client.finnhub.dto;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.tradelite.common.StockSymbol;

class PriceQuoteResponseTest {

    @Test
    void testGettersAndSetters() {
        PriceQuoteResponse response = new PriceQuoteResponse();

        response.setStockSymbol(StockSymbol.META);
        response.setCurrentPrice(123.45);
        response.setDailyOpen(120.00);
        response.setDailyHigh(125.00);
        response.setDailyLow(119.00);
        response.setChange(3.45);
        response.setChangePercent(2.87);
        response.setPreviousClose(120.00);

        assertThat(response.getStockSymbol(), is(StockSymbol.META));
        assertThat(response.getCurrentPrice(), is(123.45));
        assertThat(response.getDailyOpen(), is(120.00));
        assertThat(response.getDailyHigh(), is(125.00));
        assertThat(response.getDailyLow(), is(119.00));
        assertThat(response.getChange(), is(3.45));
        assertThat(response.getChangePercent(), is(2.87));
        assertThat(response.getPreviousClose(), is(120.00));
    }

    @Test
    void isValid_valid() {
        PriceQuoteResponse dto = new PriceQuoteResponse();
        dto.setStockSymbol(StockSymbol.META);
        dto.setCurrentPrice(123.45);

        assertTrue(dto.isValid());
    }

    @Test
    void isValid_invalid_negativePrice() {
        PriceQuoteResponse dto = new PriceQuoteResponse();
        dto.setStockSymbol(StockSymbol.AVGO);
        dto.setCurrentPrice(-1.0);

        assertFalse(dto.isValid());
    }

    @Test
    void isValid_invalid_nullSymbol() {
        PriceQuoteResponse dto = new PriceQuoteResponse();
        dto.setCurrentPrice(123.45);

        assertFalse(dto.isValid());
    }
}
