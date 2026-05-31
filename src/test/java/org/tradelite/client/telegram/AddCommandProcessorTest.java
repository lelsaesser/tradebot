package org.tradelite.client.telegram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.client.yahoo.YahooFetchException;
import org.tradelite.client.yahoo.YahooFinanceClient;
import org.tradelite.common.AssetType;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.NewlyAddedSymbolRepository;

@ExtendWith(MockitoExtension.class)
class AddCommandProcessorTest {

    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramGateway telegramClient;
    @Mock private SymbolRegistry symbolRegistry;
    @Mock private FinnhubClient finnhubClient;
    @Mock private CoinGeckoClient coinGeckoClient;
    @Mock private YahooFinanceClient yahooFinanceClient;
    @Mock private NewlyAddedSymbolRepository newlyAddedSymbolRepository;

    private AddCommandProcessor addCommandProcessor;

    @BeforeEach
    void setUp() {
        addCommandProcessor =
                new AddCommandProcessor(
                        targetPriceProvider,
                        telegramClient,
                        symbolRegistry,
                        finnhubClient,
                        coinGeckoClient,
                        yahooFinanceClient,
                        newlyAddedSymbolRepository);
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
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class))).thenReturn(Optional.of(mockQuote));

        when(symbolRegistry.addSymbol("COHR", "Coherent Corp")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), any(AssetType.class)))
                .thenReturn(true);

        addCommandProcessor.processCommand(command);

        verify(finnhubClient).tryGetPriceQuote(any(StockSymbol.class));
        verify(symbolRegistry).addSymbol("COHR", "Coherent Corp");
        verify(targetPriceProvider).addTargetPrice(any(TargetPrice.class), any(AssetType.class));
        verify(newlyAddedSymbolRepository).insert(eq("COHR"), anyLong());
        verify(telegramClient)
                .sendMessage(
                        "All set!\nAdded Coherent Corp (COHR) with buy target 0.0 and sell target 0.0.");
    }

    @Test
    void processCommand_invalidTicker_doesNotInsertNewlyAdded() {
        AddCommand command = new AddCommand("INVALID", "Invalid Ticker", 0.0, 0.0);

        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class))).thenReturn(Optional.empty());

        addCommandProcessor.processCommand(command);

        verify(newlyAddedSymbolRepository, never()).insert(anyString(), anyLong());
    }

    @Test
    void processCommand_invalidTicker_sendsErrorMessage() {
        AddCommand command = new AddCommand("INVALID", "Invalid Ticker", 0.0, 0.0);

        // Finnhub returns empty (unknown symbol); CoinGecko will fail on CoinId.fromString
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class))).thenReturn(Optional.empty());

        addCommandProcessor.processCommand(command);

        verify(telegramClient)
                .sendMessage(
                        "Invalid ticker symbol: INVALID. Could not fetch price data from Finnhub or CoinGecko.");
        verify(symbolRegistry, never()).addSymbol(anyString(), anyString());
    }

    @Test
    void processCommand_symbolAlreadyExists_sendsErrorMessage() {
        AddCommand command = new AddCommand("AAPL", "Apple", 0.0, 0.0);

        // Mock successful validation
        PriceQuoteResponse mockQuote = new PriceQuoteResponse();
        mockQuote.setStockSymbol(new StockSymbol("AAPL", "Apple"));
        mockQuote.setCurrentPrice(150.0);
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class))).thenReturn(Optional.of(mockQuote));

        when(symbolRegistry.addSymbol("AAPL", "Apple")).thenReturn(false);

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
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class))).thenReturn(Optional.of(mockQuote));

        when(symbolRegistry.addSymbol("COHR", "Coherent Corp")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), any(AssetType.class)))
                .thenReturn(false);

        addCommandProcessor.processCommand(command);

        verify(symbolRegistry).addSymbol("COHR", "Coherent Corp");
        verify(symbolRegistry).removeSymbol("COHR");
        verify(newlyAddedSymbolRepository, never()).insert(anyString(), anyLong());
        verify(telegramClient).sendMessage("Failed to add symbol to target prices: COHR");
    }

    @Test
    void processCommand_validViaCoinGecko_updatesTargetPrice() {
        AddCommand command = new AddCommand("bitcoin", "Bitcoin", 0.0, 0.0);

        // Mock failed Finnhub validation
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class))).thenReturn(Optional.empty());

        // Mock successful CoinGecko validation
        CoinGeckoPriceResponse.CoinData mockCoinData = new CoinGeckoPriceResponse.CoinData();
        mockCoinData.setUsd(50000.0);
        when(coinGeckoClient.getCoinPriceData(any())).thenReturn(mockCoinData);

        when(symbolRegistry.addSymbol("bitcoin", "Bitcoin")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), any(AssetType.class)))
                .thenReturn(true);

        addCommandProcessor.processCommand(command);

        verify(finnhubClient).tryGetPriceQuote(any(StockSymbol.class));
        verify(coinGeckoClient).getCoinPriceData(any());
        verify(symbolRegistry).addSymbol("bitcoin", "Bitcoin");
        verify(targetPriceProvider).addTargetPrice(any(TargetPrice.class), any(AssetType.class));
        verify(telegramClient)
                .sendMessage(
                        "All set!\nAdded Bitcoin (bitcoin) with buy target 0.0 and sell target 0.0.");
    }

    @Test
    void processCommand_internationalTicker_validViaYahoo_succeeds() {
        AddCommand command = new AddCommand("RHM.DE", "Rheinmetall", 400.0, 500.0);

        // Mock international symbol detection
        when(symbolRegistry.isInternationalSymbol("RHM.DE")).thenReturn(true);

        // Mock successful Yahoo validation
        OhlcvRecord ohlcvRecord =
                new OhlcvRecord(
                        "RHM.DE", java.time.LocalDate.now(), 450.0, 460.0, 440.0, 455.0, 100000L);
        when(yahooFinanceClient.fetchDailyOhlcv("RHM.DE", 5)).thenReturn(List.of(ohlcvRecord));

        when(symbolRegistry.addSymbol("RHM.DE", "Rheinmetall")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), any(AssetType.class)))
                .thenReturn(true);

        addCommandProcessor.processCommand(command);

        verify(yahooFinanceClient).fetchDailyOhlcv("RHM.DE", 5);
        verify(finnhubClient, never()).tryGetPriceQuote(any(StockSymbol.class));
        verify(coinGeckoClient, never()).getCoinPriceData(any());
        verify(symbolRegistry).addSymbol("RHM.DE", "Rheinmetall");
        verify(telegramClient)
                .sendMessage(
                        "All set!\nAdded Rheinmetall (RHM.DE) with buy target 400.0 and sell target 500.0.");
    }

    @Test
    void processCommand_internationalTicker_yahooFails_sendsYahooErrorMessage() {
        AddCommand command = new AddCommand("INVALID.XY", "Fake Stock", 100.0, 200.0);

        // Mock international symbol detection
        when(symbolRegistry.isInternationalSymbol("INVALID.XY")).thenReturn(true);

        // Mock failed Yahoo validation
        when(yahooFinanceClient.fetchDailyOhlcv("INVALID.XY", 5))
                .thenThrow(new YahooFetchException("INVALID.XY", "curl exited with code 22"));

        addCommandProcessor.processCommand(command);

        verify(yahooFinanceClient).fetchDailyOhlcv("INVALID.XY", 5);
        verify(finnhubClient, never()).tryGetPriceQuote(any(StockSymbol.class));
        verify(telegramClient)
                .sendMessage(
                        "Invalid ticker symbol: INVALID.XY. Could not fetch price data from Yahoo Finance.");
        verify(symbolRegistry, never()).addSymbol(anyString(), anyString());
    }
}
