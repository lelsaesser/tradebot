package org.tradelite.client.finnhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.tradelite.common.StockSymbol;

@Getter
@Setter
public class PriceQuoteResponse {

    StockSymbol stockSymbol;

    @JsonProperty("c")
    double currentPrice;

    @JsonProperty("o")
    double dailyOpen;

    @JsonProperty("h")
    double dailyHigh;

    @JsonProperty("l")
    double dailyLow;

    @JsonProperty("d")
    double change;

    @JsonProperty("dp")
    double changePercent;

    @JsonProperty("pc")
    double previousClose;

    public boolean isValid() {
        return stockSymbol != null && currentPrice >= 0;
    }
}
