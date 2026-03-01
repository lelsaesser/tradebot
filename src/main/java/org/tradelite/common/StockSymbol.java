package org.tradelite.common;

import java.util.Objects;
import lombok.Getter;

@Getter
public class StockSymbol implements TickerSymbol {

    private final String ticker;
    private final String companyName;

    public StockSymbol(String ticker, String companyName) {
        this.ticker = ticker;
        this.companyName = companyName;
    }

    @Override
    public String getName() {
        return ticker;
    }

    public String getDisplayName() {
        return String.format("%s (%s)", companyName, ticker);
    }

    @Override
    public SymbolType getSymbolType() {
        return SymbolType.STOCK;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockSymbol that = (StockSymbol) o;
        return Objects.equals(ticker, that.ticker);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticker);
    }

    @Override
    public String toString() {
        return ticker;
    }
}
