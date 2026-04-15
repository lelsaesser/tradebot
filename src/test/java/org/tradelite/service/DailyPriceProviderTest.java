package org.tradelite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.repository.OhlcvRepository;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.model.DailyPrice;

@ExtendWith(MockitoExtension.class)
class DailyPriceProviderTest {

    @Mock private OhlcvRepository ohlcvRepository;
    @Mock private PriceQuoteRepository priceQuoteRepository;

    private DailyPriceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DailyPriceProvider(ohlcvRepository, priceQuoteRepository);
    }

    @Test
    void findDailyClosingPrices_ohlcvPresent_returnsOhlcvClosePrices() {
        LocalDate date1 = LocalDate.of(2026, 4, 10);
        LocalDate date2 = LocalDate.of(2026, 4, 11);
        when(ohlcvRepository.findBySymbol("AAPL", 90))
                .thenReturn(
                        List.of(
                                new OhlcvRecord("AAPL", date1, 150.0, 155.0, 149.0, 153.0, 1000000),
                                new OhlcvRecord(
                                        "AAPL", date2, 153.0, 157.0, 152.0, 156.0, 1200000)));

        List<DailyPrice> result = provider.findDailyClosingPrices("AAPL", 90);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDate()).isEqualTo(date1);
        assertThat(result.get(0).getPrice()).isEqualTo(153.0);
        assertThat(result.get(1).getDate()).isEqualTo(date2);
        assertThat(result.get(1).getPrice()).isEqualTo(156.0);
        verify(priceQuoteRepository, never()).findDailyClosingPrices(anyString(), anyInt());
    }

    @Test
    void findDailyClosingPrices_ohlcvEmpty_fallsBackToFinnhub() {
        when(ohlcvRepository.findBySymbol("XLK", 90)).thenReturn(List.of());
        DailyPrice finnhubPrice = new DailyPrice(LocalDate.of(2026, 4, 10), 200.0);
        when(priceQuoteRepository.findDailyClosingPrices("XLK", 90))
                .thenReturn(List.of(finnhubPrice));

        List<DailyPrice> result = provider.findDailyClosingPrices("XLK", 90);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPrice()).isEqualTo(200.0);
        verify(priceQuoteRepository).findDailyClosingPrices("XLK", 90);
    }

    @Test
    void findDailyClosingPrices_bothEmpty_returnsEmptyList() {
        when(ohlcvRepository.findBySymbol("UNKNOWN", 90)).thenReturn(List.of());
        when(priceQuoteRepository.findDailyClosingPrices("UNKNOWN", 90)).thenReturn(List.of());

        List<DailyPrice> result = provider.findDailyClosingPrices("UNKNOWN", 90);

        assertThat(result).isEmpty();
    }

    @Test
    void findDailyClosingPrices_ohlcvPresent_preservesDateOrder() {
        LocalDate date1 = LocalDate.of(2026, 4, 8);
        LocalDate date2 = LocalDate.of(2026, 4, 9);
        LocalDate date3 = LocalDate.of(2026, 4, 10);
        when(ohlcvRepository.findBySymbol("MSFT", 30))
                .thenReturn(
                        List.of(
                                new OhlcvRecord("MSFT", date1, 400.0, 405.0, 398.0, 402.0, 500000),
                                new OhlcvRecord("MSFT", date2, 402.0, 410.0, 401.0, 408.0, 600000),
                                new OhlcvRecord(
                                        "MSFT", date3, 408.0, 412.0, 406.0, 410.0, 550000)));

        List<DailyPrice> result = provider.findDailyClosingPrices("MSFT", 30);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDate()).isBefore(result.get(1).getDate());
        assertThat(result.get(1).getDate()).isBefore(result.get(2).getDate());
    }
}
