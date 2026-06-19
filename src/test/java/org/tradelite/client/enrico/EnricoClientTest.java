package org.tradelite.client.enrico;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.enrico.dto.EnricoHolidayDto;
import org.tradelite.common.Exchange;

@ExtendWith(MockitoExtension.class)
class EnricoClientTest {

    @Mock private RestTemplate restTemplate;

    private EnricoClient client;

    @BeforeEach
    void setUp() {
        client = new EnricoClient(restTemplate);
    }

    @Test
    void getHolidaysForRange_validResponse_returnsParsedMap() {
        EnricoHolidayDto[] response = {
            new EnricoHolidayDto(
                    new EnricoHolidayDto.DateDto(1, 1, 2026),
                    List.of(
                            new EnricoHolidayDto.NameDto("de", "Neujahr"),
                            new EnricoHolidayDto.NameDto("en", "New Year's Day"))),
            new EnricoHolidayDto(
                    new EnricoHolidayDto.DateDto(1, 5, 2026),
                    List.of(
                            new EnricoHolidayDto.NameDto("de", "Tag der Arbeit"),
                            new EnricoHolidayDto.NameDto("en", "Labour Day")))
        };
        when(restTemplate.getForEntity(any(String.class), eq(EnricoHolidayDto[].class)))
                .thenReturn(ResponseEntity.ok(response));

        Map<LocalDate, String> holidays =
                client.getHolidaysForRange(
                        Exchange.XETRA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(holidays.size(), is(2));
        assertThat(holidays.get(LocalDate.of(2026, 1, 1)), is("New Year's Day"));
        assertThat(holidays.get(LocalDate.of(2026, 5, 1)), is("Labour Day"));
    }

    @Test
    void getHolidaysForRange_urlContainsExpectedQueryParameters() {
        when(restTemplate.getForEntity(any(String.class), eq(EnricoHolidayDto[].class)))
                .thenReturn(ResponseEntity.ok(new EnricoHolidayDto[0]));

        client.getHolidaysForRange(
                Exchange.KRX, LocalDate.of(2026, 6, 18), LocalDate.of(2027, 6, 18));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(restTemplate)
                .getForEntity(urlCaptor.capture(), eq(EnricoHolidayDto[].class));
        String url = urlCaptor.getValue();
        assertThat(url, containsString("kayaposoft.com/enrico/json/v2.0"));
        assertThat(url, containsString("action=getHolidaysForDateRange"));
        assertThat(url, containsString("fromDate=18-06-2026"));
        assertThat(url, containsString("toDate=18-06-2027"));
        assertThat(url, containsString("country=kor"));
        assertThat(url, containsString("holidayType=public_holiday"));
    }

    @Test
    void getHolidaysForRange_noEnglishName_fallsBackToFirstAvailable() {
        EnricoHolidayDto[] response = {
            new EnricoHolidayDto(
                    new EnricoHolidayDto.DateDto(15, 8, 2026),
                    List.of(new EnricoHolidayDto.NameDto("ko", "광복절")))
        };
        when(restTemplate.getForEntity(any(String.class), eq(EnricoHolidayDto[].class)))
                .thenReturn(ResponseEntity.ok(response));

        Map<LocalDate, String> holidays =
                client.getHolidaysForRange(
                        Exchange.KRX, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(holidays.get(LocalDate.of(2026, 8, 15)), is("광복절"));
    }

    @Test
    void getHolidaysForRange_emptyNameArray_returnsPlaceholder() {
        EnricoHolidayDto[] response = {
            new EnricoHolidayDto(new EnricoHolidayDto.DateDto(1, 1, 2026), List.of())
        };
        when(restTemplate.getForEntity(any(String.class), eq(EnricoHolidayDto[].class)))
                .thenReturn(ResponseEntity.ok(response));

        Map<LocalDate, String> holidays =
                client.getHolidaysForRange(
                        Exchange.PAR, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(holidays.get(LocalDate.of(2026, 1, 1)), is("Holiday"));
    }

    @Test
    void getHolidaysForRange_nullNameArray_returnsPlaceholder() {
        EnricoHolidayDto[] response = {
            new EnricoHolidayDto(new EnricoHolidayDto.DateDto(1, 1, 2026), null)
        };
        when(restTemplate.getForEntity(any(String.class), eq(EnricoHolidayDto[].class)))
                .thenReturn(ResponseEntity.ok(response));

        Map<LocalDate, String> holidays =
                client.getHolidaysForRange(
                        Exchange.PAR, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(holidays.get(LocalDate.of(2026, 1, 1)), is("Holiday"));
    }

    @Test
    void getHolidaysForRange_nullDate_skipsEntry() {
        EnricoHolidayDto[] response = {
            new EnricoHolidayDto(null, List.of(new EnricoHolidayDto.NameDto("en", "Bogus"))),
            new EnricoHolidayDto(
                    new EnricoHolidayDto.DateDto(1, 1, 2026),
                    List.of(new EnricoHolidayDto.NameDto("en", "New Year's Day")))
        };
        when(restTemplate.getForEntity(any(String.class), eq(EnricoHolidayDto[].class)))
                .thenReturn(ResponseEntity.ok(response));

        Map<LocalDate, String> holidays =
                client.getHolidaysForRange(
                        Exchange.PAR, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(holidays.size(), is(1));
        assertThat(holidays.get(LocalDate.of(2026, 1, 1)), is("New Year's Day"));
    }

    @Test
    void getHolidaysForRange_restTemplateThrows_returnsEmptyMap() {
        when(restTemplate.getForEntity(any(String.class), eq(EnricoHolidayDto[].class)))
                .thenThrow(new RuntimeException("connection refused"));

        Map<LocalDate, String> holidays =
                client.getHolidaysForRange(
                        Exchange.STO, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(holidays, is(anEmptyMap()));
    }

    @Test
    void getHolidaysForRange_nullBody_returnsEmptyMap() {
        when(restTemplate.getForEntity(any(String.class), eq(EnricoHolidayDto[].class)))
                .thenReturn(ResponseEntity.ok(null));

        Map<LocalDate, String> holidays =
                client.getHolidaysForRange(
                        Exchange.JPX, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(holidays, is(anEmptyMap()));
    }

    @Test
    void getHolidaysForRange_non2xxResponse_returnsEmptyMap() {
        when(restTemplate.getForEntity(any(String.class), eq(EnricoHolidayDto[].class)))
                .thenReturn(
                        new ResponseEntity<>(
                                new EnricoHolidayDto[0], HttpStatus.SERVICE_UNAVAILABLE));

        Map<LocalDate, String> holidays =
                client.getHolidaysForRange(
                        Exchange.JPX, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(holidays, is(anEmptyMap()));
    }

    // ---- Real-network integration test -----------------------------------------------------
    // Mirrors YahooFinanceClientTest.executeRequest_realYahooCall_returnsValidJson — fast disproof
    // if Enrico's response shape ever changes, rather than waiting for production startup.

    @Test
    void getHolidaysForRange_realEnricoCall_returnsKnownHolidays() {
        EnricoClient realClient = new EnricoClient(new RestTemplate());

        // 2026-01-01 is New Year's Day in every supported country — a stable smoke check.
        Map<LocalDate, String> holidays =
                realClient.getHolidaysForRange(
                        Exchange.XETRA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(holidays, is(not(anEmptyMap())));
        assertThat(holidays.containsKey(LocalDate.of(2026, 1, 1)), is(true));
        assertThat(holidays.get(LocalDate.of(2026, 1, 1)), is("New Year's Day"));
    }
}
