package org.tradelite.client.yahoo;

public class YahooFetchException extends RuntimeException {

    public YahooFetchException(String symbol, String reason) {
        super("Yahoo Finance fetch failed for " + symbol + ": " + reason);
    }
}
