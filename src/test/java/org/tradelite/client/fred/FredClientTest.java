package org.tradelite.client.fred;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.fred.dto.FredObservationDto;
import org.tradelite.client.fred.dto.FredObservationsResponseDto;
import org.tradelite.config.TradebotApiProperties;

@ExtendWith(MockitoExtension.class)
class FredClientTest {

    @Mock private RestTemplate restTemplate;

    private FredClient client;

    @BeforeEach
    void setUp() {
        TradebotApiProperties props = new TradebotApiProperties();
        props.setFredKey("test-fred-key");
        client = new FredClient(restTemplate, props);
    }

    @Test
    void fetchObservations_validResponse_returnsParsedList() {
        FredObservationsResponseDto body =
                new FredObservationsResponseDto(
                        List.of(
                                new FredObservationDto("2026-06-23", "0.65"),
                                new FredObservationDto("2026-06-22", "0.66"),
                                new FredObservationDto("2026-06-19", "0.68")));
        when(restTemplate.getForEntity(any(String.class), eq(FredObservationsResponseDto.class)))
                .thenReturn(ResponseEntity.ok(body));

        List<FredObservation> result =
                client.fetchObservations(
                        "T10Y3M", LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 23));

        assertThat(result, hasSize(3));
        assertThat(result.get(0).date(), is(LocalDate.of(2026, 6, 23)));
        assertThat(result.get(0).value(), is(0.65));
        assertThat(result.get(2).date(), is(LocalDate.of(2026, 6, 19)));
        assertThat(result.get(2).value(), is(0.68));
    }

    @Test
    void fetchObservations_dotSentinelRows_areFiltered() {
        // Real FRED response shape: weekend / holiday rows carry "." in the value field.
        FredObservationsResponseDto body =
                new FredObservationsResponseDto(
                        List.of(
                                new FredObservationDto("2026-06-23", "0.65"),
                                new FredObservationDto("2026-06-22", "0.66"),
                                new FredObservationDto("2026-06-19", "."),
                                new FredObservationDto("2026-06-18", "0.70")));
        when(restTemplate.getForEntity(any(String.class), eq(FredObservationsResponseDto.class)))
                .thenReturn(ResponseEntity.ok(body));

        List<FredObservation> result =
                client.fetchObservations(
                        "T10Y3M", LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 23));

        assertThat(result, hasSize(3));
        // The "." row at 2026-06-19 is absent.
        for (FredObservation obs : result) {
            assertThat(obs.date(), is(not(LocalDate.of(2026, 6, 19))));
        }
    }

    @Test
    void fetchObservations_urlContainsExpectedQueryParameters() {
        when(restTemplate.getForEntity(any(String.class), eq(FredObservationsResponseDto.class)))
                .thenReturn(ResponseEntity.ok(new FredObservationsResponseDto(List.of())));

        client.fetchObservations("DFII10", LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 23));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(restTemplate)
                .getForEntity(urlCaptor.capture(), eq(FredObservationsResponseDto.class));
        String url = urlCaptor.getValue();
        assertThat(url, containsString("api.stlouisfed.org/fred/series/observations"));
        assertThat(url, containsString("series_id=DFII10"));
        assertThat(url, containsString("api_key=test-fred-key"));
        assertThat(url, containsString("file_type=json"));
        assertThat(url, containsString("sort_order=desc"));
        assertThat(url, containsString("observation_start=2026-06-16"));
        assertThat(url, containsString("observation_end=2026-06-23"));
    }

    @Test
    void fetchObservations_restTemplateThrows_returnsEmptyList() {
        when(restTemplate.getForEntity(any(String.class), eq(FredObservationsResponseDto.class)))
                .thenThrow(new RuntimeException("connection refused"));

        List<FredObservation> result =
                client.fetchObservations(
                        "T10Y3M", LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 23));

        assertThat(result, is(empty()));
    }

    @Test
    void fetchObservations_nullBody_returnsEmptyList() {
        when(restTemplate.getForEntity(any(String.class), eq(FredObservationsResponseDto.class)))
                .thenReturn(ResponseEntity.ok(null));

        List<FredObservation> result =
                client.fetchObservations(
                        "T10Y2Y", LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 23));

        assertThat(result, is(empty()));
    }

    @Test
    void fetchObservations_non2xxResponse_returnsEmptyList() {
        when(restTemplate.getForEntity(any(String.class), eq(FredObservationsResponseDto.class)))
                .thenReturn(
                        new ResponseEntity<>(
                                new FredObservationsResponseDto(List.of()),
                                HttpStatus.SERVICE_UNAVAILABLE));

        List<FredObservation> result =
                client.fetchObservations(
                        "DFII10", LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 23));

        assertThat(result, is(empty()));
    }

    @Test
    void fetchObservations_emptyObservations_returnsEmptyList() {
        when(restTemplate.getForEntity(any(String.class), eq(FredObservationsResponseDto.class)))
                .thenReturn(ResponseEntity.ok(new FredObservationsResponseDto(List.of())));

        List<FredObservation> result =
                client.fetchObservations(
                        "THREEFYTP10", LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 23));

        assertThat(result, is(empty()));
    }

    @Test
    void fetchObservations_unparseableRow_isSkippedNotCrashing() {
        FredObservationsResponseDto body =
                new FredObservationsResponseDto(
                        List.of(
                                new FredObservationDto("2026-06-23", "0.65"),
                                new FredObservationDto("not-a-date", "0.66"),
                                new FredObservationDto("2026-06-22", "not-a-number"),
                                new FredObservationDto("2026-06-18", "0.70")));
        when(restTemplate.getForEntity(any(String.class), eq(FredObservationsResponseDto.class)))
                .thenReturn(ResponseEntity.ok(body));

        List<FredObservation> result =
                client.fetchObservations(
                        "T10Y3M", LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 23));

        assertThat(result, hasSize(2));
        assertThat(result.get(0).value(), is(0.65));
        assertThat(result.get(1).value(), is(0.70));
    }
}
