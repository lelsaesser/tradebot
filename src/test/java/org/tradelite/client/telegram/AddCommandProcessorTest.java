package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.StockSymbolRegistry;

@ExtendWith(MockitoExtension.class)
class AddCommandProcessorTest {

    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramGateway telegramClient;
    @Mock private StockSymbolRegistry stockSymbolRegistry;
    @Mock private FinnhubClient finnhubClient;
    @Mock private CoinGeckoClient coinGeckoClient;

    private AddCommandProcessor addCommandProcessor;

    @BeforeEach
    void setUp() {
        addCommandProcessor =
                new AddCommandProcessor(
                        targetPriceProvider,
                        telegramClient,
                        stockSymbolRegistry,
                        finnhubClient,
                        coinGeckoClient);
    }

    @Test
    void canProcess_addCommand_returnsTrue() {
        AddCommand command = new AddCommand("AAPL", "Apple", 0, 0);
        boolean result = addCommandProcessor.canProcess(command);

        assertThat(result, is(true));
    }

    @Test
    void canProcess_nonAddCommand_returnsFalse() {
        SetCommand command = new SetCommand("buy", "BITCOIN", 50000.0);
        boolean result = addCommandProcessor.canProcess(command);

        assertThat(result, is(false));
    }

    @Test
    void processCommand_validAddCommand_updatesTargetPrice() {
        AddCommand command = new AddCommand("COHR", "Coherent Corp", 0.0, 0.0);

        // Mock successful Finnhub validation
        PriceQuoteResponse mockQuote = new PriceQuoteResponse();
        mockQuote.setStockSymbol(new StockSymbol("COHR", "Coherent Corp"));
        mockQuote.setCurrentPrice(100.0);
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenReturn(mockQuote);

        when(stockSymbolRegistry.addSymbol("COHR", "Coherent Corp")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), anyString()))
                .thenReturn(true);

        addCommandProcessor.processCommand(command);

        verify(finnhubClient).getPriceQuote(any(StockSymbol.class));
        verify(stockSymbolRegistry).addSymbol("COHR", "Coherent Corp");
        verify(targetPriceProvider).addTargetPrice(any(TargetPrice.class), anyString());
        verify(telegramClient)
                .sendMessage(
                        "All set!\nAdded Coherent Corp (COHR) with buy target 0.0 and sell target 0.0.");
    }

    @Test
    void processCommand_invalidTicker_sendsErrorMessage() {
        AddCommand command = new AddCommand("INVALID", "Invalid Ticker", 0.0, 0.0);

        // Mock failed validation from Finnhub (CoinGecko will fail on CoinId.fromString)
        when(finnhubClient.getPriceQuote(any(StockSymbol.class)))
                .thenThrow(new RuntimeException("Not found"));

        addCommandProcessor.processCommand(command);

        verify(telegramClient)
                .sendMessage(
                        "Invalid ticker symbol: INVALID. Could not fetch price data from Finnhub or CoinGecko.");
        verify(stockSymbolRegistry, never()).addSymbol(anyString(), anyString());
    }

    @Test
    void processCommand_symbolAlreadyExists_sendsErrorMessage() {
        AddCommand command = new AddCommand("AAPL", "Apple", 0.0, 0.0);

        // Mock successful validation
        PriceQuoteResponse mockQuote = new PriceQuoteResponse();
        mockQuote.setStockSymbol(new StockSymbol("AAPL", "Apple"));
        mockQuote.setCurrentPrice(150.0);
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenReturn(mockQuote);

        when(stockSymbolRegistry.addSymbol("AAPL", "Apple")).thenReturn(false);

        addCommandProcessor.processCommand(command);

        verify(telegramClient)
                .sendMessage(
                        "Failed to add symbol: AAPL. It may already exist or there was an error.");
    }

    @Test
    void processCommand_targetPriceAddFails_rollsBackSymbolAddition() {
        AddCommand command = new AddCommand("COHR", "Coherent Corp", 0.0, 0.0);

        // Mock successful validation
        PriceQuoteResponse mockQuote = new PriceQuoteResponse();
        mockQuote.setStockSymbol(new StockSymbol("COHR", "Coherent Corp"));
        mockQuote.setCurrentPrice(100.0);
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenReturn(mockQuote);

        when(stockSymbolRegistry.addSymbol("COHR", "Coherent Corp")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), anyString()))
                .thenReturn(false);

        addCommandProcessor.processCommand(command);

        verify(stockSymbolRegistry).addSymbol("COHR", "Coherent Corp");
        verify(stockSymbolRegistry).removeSymbol("COHR");
        verify(telegramClient).sendMessage("Failed to add symbol to target prices: COHR");
    }

    @Test
    void processCommand_validViaCoinGecko_updatesTargetPrice() {
        AddCommand command = new AddCommand("bitcoin", "Bitcoin", 0.0, 0.0);

        // Mock failed Finnhub validation
        when(finnhubClient.getPriceQuote(any(StockSymbol.class)))
                .thenThrow(new RuntimeException("Not a stock"));

        // Mock successful CoinGecko validation
        CoinGeckoPriceResponse.CoinData mockCoinData = new CoinGeckoPriceResponse.CoinData();
        mockCoinData.setUsd(50000.0);
        when(coinGeckoClient.getCoinPriceData(any())).thenReturn(mockCoinData);

        when(stockSymbolRegistry.addSymbol("bitcoin", "Bitcoin")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), anyString()))
                .thenReturn(true);

        addCommandProcessor.processCommand(command);

        verify(finnhubClient).getPriceQuote(any(StockSymbol.class));
        verify(coinGeckoClient).getCoinPriceData(any());
        verify(stockSymbolRegistry).addSymbol("bitcoin", "Bitcoin");
        verify(targetPriceProvider).addTargetPrice(any(TargetPrice.class), anyString());
        verify(telegramClient)
                .sendMessage(
                        "All set!\nAdded Bitcoin (bitcoin) with buy target 0.0 and sell target 0.0.");
    }
}
