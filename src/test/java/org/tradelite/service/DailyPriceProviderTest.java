package org.tradelite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.repository.OhlcvRepository;
import org.tradelite.repository.PriceQuoteEntity;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.model.DailyPrice;

@ExtendWith(MockitoExtension.class)
class DailyPriceProviderTest {

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

    @Mock private OhlcvRepository ohlcvRepository;
    @Mock private PriceQuoteRepository priceQuoteRepository;

    private DailyPriceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DailyPriceProvider(ohlcvRepository, priceQuoteRepository);
    }

    @Test
    void findDailyClosingPrices_ohlcvPresent_appendsFinnhubForNewDay() {
        LocalDate yesterday = LocalDate.of(2026, 4, 15);
        LocalDate today = LocalDate.of(2026, 4, 16);
        when(ohlcvRepository.findBySymbol("AAPL", 90))
                .thenReturn(
                        List.of(
                                new OhlcvRecord(
                                        "AAPL", yesterday, 150.0, 155.0, 149.0, 153.0, 1000000)));
        when(priceQuoteRepository.findLatestBySymbol("AAPL"))
                .thenReturn(
                        Optional.of(
                                PriceQuoteEntity.builder()
                                        .symbol("AAPL")
                                        .timestamp(toEpochSecond(today, 15, 30))
                                        .currentPrice(157.0)
                                        .build()));

        List<DailyPrice> result = provider.findDailyClosingPrices("AAPL", 90);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDate()).isEqualTo(yesterday);
        assertThat(result.get(0).getPrice()).isEqualTo(153.0);
        assertThat(result.get(1).getDate()).isEqualTo(today);
        assertThat(result.get(1).getPrice()).isEqualTo(157.0);
        verify(priceQuoteRepository, never()).findDailyClosingPrices(anyString(), anyInt());
    }

    @Test
    void findDailyClosingPrices_ohlcvPresent_finnhubSameDateAsOhlcv_notAppended() {
        LocalDate friday = LocalDate.of(2026, 4, 10);
        when(ohlcvRepository.findBySymbol("AAPL", 90))
                .thenReturn(
                        List.of(
                                new OhlcvRecord(
                                        "AAPL", friday, 150.0, 155.0, 149.0, 153.0, 1000000)));
        when(priceQuoteRepository.findLatestBySymbol("AAPL"))
                .thenReturn(
                        Optional.of(
                                PriceQuoteEntity.builder()
                                        .symbol("AAPL")
                                        .timestamp(toEpochSecond(friday, 15, 59))
                                        .currentPrice(154.0)
                                        .build()));

        List<DailyPrice> result = provider.findDailyClosingPrices("AAPL", 90);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getDate()).isEqualTo(friday);
        assertThat(result.getFirst().getPrice()).isEqualTo(153.0);
    }

    @Test
    void findDailyClosingPrices_ohlcvPresent_finnhubEmpty_returnsOhlcvOnly() {
        LocalDate date1 = LocalDate.of(2026, 4, 10);
        LocalDate date2 = LocalDate.of(2026, 4, 11);
        when(ohlcvRepository.findBySymbol("AAPL", 90))
                .thenReturn(
                        List.of(
                                new OhlcvRecord("AAPL", date1, 150.0, 155.0, 149.0, 153.0, 1000000),
                                new OhlcvRecord(
                                        "AAPL", date2, 153.0, 157.0, 152.0, 156.0, 1200000)));
        when(priceQuoteRepository.findLatestBySymbol("AAPL")).thenReturn(Optional.empty());

        List<DailyPrice> result = provider.findDailyClosingPrices("AAPL", 90);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDate()).isEqualTo(date1);
        assertThat(result.get(0).getPrice()).isEqualTo(153.0);
        assertThat(result.get(1).getDate()).isEqualTo(date2);
        assertThat(result.get(1).getPrice()).isEqualTo(156.0);
        verify(priceQuoteRepository, never()).findDailyClosingPrices(anyString(), anyInt());
    }

    @Test
    void findDailyClosingPrices_ohlcvPresent_finnhubOlderThanOhlcv_notAppended() {
        LocalDate wednesday = LocalDate.of(2026, 4, 15);
        LocalDate tuesday = LocalDate.of(2026, 4, 14);
        when(ohlcvRepository.findBySymbol("AAPL", 90))
                .thenReturn(
                        List.of(
                                new OhlcvRecord(
                                        "AAPL",
                                        wednesday,
                                        150.0,
                                        155.0,
                                        149.0,
                                        153.0,
                                        1000000)));
        when(priceQuoteRepository.findLatestBySymbol("AAPL"))
                .thenReturn(
                        Optional.of(
                                PriceQuoteEntity.builder()
                                        .symbol("AAPL")
                                        .timestamp(toEpochSecond(tuesday, 15, 30))
                                        .currentPrice(148.0)
                                        .build()));

        List<DailyPrice> result = provider.findDailyClosingPrices("AAPL", 90);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getDate()).isEqualTo(wednesday);
        assertThat(result.getFirst().getPrice()).isEqualTo(153.0);
    }

    @Test
    void findDailyClosingPrices_ohlcvPresent_appendPreservesSortOrder() {
        LocalDate date1 = LocalDate.of(2026, 4, 13);
        LocalDate date2 = LocalDate.of(2026, 4, 14);
        LocalDate date3 = LocalDate.of(2026, 4, 15);
        LocalDate today = LocalDate.of(2026, 4, 16);
        when(ohlcvRepository.findBySymbol("MSFT", 30))
                .thenReturn(
                        List.of(
                                new OhlcvRecord("MSFT", date1, 400.0, 405.0, 398.0, 402.0, 500000),
                                new OhlcvRecord("MSFT", date2, 402.0, 410.0, 401.0, 408.0, 600000),
                                new OhlcvRecord(
                                        "MSFT", date3, 408.0, 412.0, 406.0, 410.0, 550000)));
        when(priceQuoteRepository.findLatestBySymbol("MSFT"))
                .thenReturn(
                        Optional.of(
                                PriceQuoteEntity.builder()
                                        .symbol("MSFT")
                                        .timestamp(toEpochSecond(today, 14, 0))
                                        .currentPrice(415.0)
                                        .build()));

        List<DailyPrice> result = provider.findDailyClosingPrices("MSFT", 30);

        assertThat(result).hasSize(4);
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getDate()).isBefore(result.get(i + 1).getDate());
        }
        assertThat(result.getLast().getPrice()).isEqualTo(415.0);
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
        verify(priceQuoteRepository, never()).findLatestBySymbol(anyString());
    }

    @Test
    void findDailyClosingPrices_bothEmpty_returnsEmptyList() {
        when(ohlcvRepository.findBySymbol("UNKNOWN", 90)).thenReturn(List.of());
        when(priceQuoteRepository.findDailyClosingPrices("UNKNOWN", 90)).thenReturn(List.of());

        List<DailyPrice> result = provider.findDailyClosingPrices("UNKNOWN", 90);

        assertThat(result).isEmpty();
    }

    private long toEpochSecond(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(NY_ZONE).toEpochSecond();
    }
}
