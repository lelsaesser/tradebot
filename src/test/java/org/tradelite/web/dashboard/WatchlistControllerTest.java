package org.tradelite.web.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tradelite.common.AssetType;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.common.TargetSide;
import org.tradelite.service.LivePriceCache;
import org.tradelite.service.SymbolManagementService;
import org.tradelite.service.SymbolManagementService.AddResult;

class WatchlistControllerTest {

    SymbolRegistry symbolRegistry = mock(SymbolRegistry.class);
    TargetPriceProvider targetPriceProvider = mock(TargetPriceProvider.class);
    LivePriceCache livePriceCache = mock(LivePriceCache.class);
    SymbolManagementService symbolManagementService = mock(SymbolManagementService.class);

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WatchlistController controller =
                new WatchlistController(
                        symbolRegistry,
                        targetPriceProvider,
                        livePriceCache,
                        symbolManagementService);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setMessageConverters(new MappingJackson2HttpMessageConverter())
                        .build();
    }

    @Test
    void getWatchlist_returnsRowsWithExchangeLabels() throws Exception {
        when(symbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("AAPL", "Apple Inc"),
                                new StockSymbol("SAP.DE", "SAP SE"),
                                new StockSymbol("005930.KS", "Samsung Electronics")));
        when(symbolRegistry.isEtf(any())).thenReturn(false);
        when(livePriceCache.getAll())
                .thenReturn(
                        Map.of(
                                "AAPL", 182.0,
                                "SAP.DE", 175.0,
                                "005930.KS", 68000.0));

        TargetPrice appleTarget = new TargetPrice("AAPL", 170.0, 200.0);
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of(appleTarget));

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbols[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.symbols[0].exchange").value("US"))
                .andExpect(jsonPath("$.symbols[0].currentPrice").value(182.0))
                .andExpect(jsonPath("$.symbols[0].buyTarget").value(170.0))
                .andExpect(jsonPath("$.symbols[1].ticker").value("SAP.DE"))
                .andExpect(jsonPath("$.symbols[1].exchange").value("XETRA"))
                .andExpect(jsonPath("$.symbols[2].ticker").value("005930.KS"))
                .andExpect(jsonPath("$.symbols[2].exchange").value("KRX"));
    }

    @Test
    void addSymbol_200onSuccess() throws Exception {
        when(symbolManagementService.addSymbol(eq("MSFT"), eq("Microsoft"), any(), any()))
                .thenReturn(new AddResult(true, "Added Microsoft (MSFT)."));

        mockMvc.perform(
                        post("/api/v1/symbols")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ticker\":\"MSFT\",\"displayName\":\"Microsoft\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void addSymbol_400onFailure() throws Exception {
        when(symbolManagementService.addSymbol(eq("INVALID"), eq("Bad"), any(), any()))
                .thenReturn(new AddResult(false, "Invalid ticker: INVALID."));

        mockMvc.perform(
                        post("/api/v1/symbols")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ticker\":\"INVALID\",\"displayName\":\"Bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeSymbol_200whenFound() throws Exception {
        when(symbolManagementService.removeSymbol("AAPL")).thenReturn(true);

        mockMvc.perform(delete("/api/v1/symbols/AAPL")).andExpect(status().isOk());
    }

    @Test
    void removeSymbol_404whenNotFound() throws Exception {
        when(symbolManagementService.removeSymbol("UNKNOWN")).thenReturn(false);

        mockMvc.perform(delete("/api/v1/symbols/UNKNOWN")).andExpect(status().isNotFound());
    }

    @Test
    void setTarget_200whenSymbolFound() throws Exception {
        StockSymbol aapl = new StockSymbol("AAPL", "Apple Inc");
        when(symbolRegistry.fromString("AAPL")).thenReturn(Optional.of(aapl));

        mockMvc.perform(
                        post("/api/v1/targets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ticker\":\"AAPL\",\"side\":\"BUY\",\"price\":170.0}"))
                .andExpect(status().isOk());

        verify(targetPriceProvider)
                .updateTargetPrice(eq(aapl), eq(TargetSide.BUY), eq(170.0), eq(AssetType.STOCK));
    }

    @Test
    void setTarget_404whenSymbolNotFound() throws Exception {
        when(symbolRegistry.fromString("GHOST")).thenReturn(Optional.empty());

        mockMvc.perform(
                        post("/api/v1/targets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ticker\":\"GHOST\",\"side\":\"SELL\",\"price\":50.0}"))
                .andExpect(status().isNotFound());
    }
}
