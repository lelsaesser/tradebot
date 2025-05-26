package org.tradelite.client.finnhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.tradelite.common.TickerSymbol;

@Getter
@Setter
public class PriceQuoteResponse {

    TickerSymbol tickerSymbol;

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
}
