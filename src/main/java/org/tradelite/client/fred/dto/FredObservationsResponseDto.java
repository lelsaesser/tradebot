package org.tradelite.client.fred.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Wire-format envelope returned by {@code /fred/series/observations}. The full envelope carries
 * paging metadata ({@code count}, {@code offset}, {@code limit}, etc.) that we don't need — {@link
 * FredObservationDto}s in {@link #observations} are the only field consumed.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} future-proofs against FRED adding new
 * envelope fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FredObservationsResponseDto(
        @JsonProperty("observations") List<FredObservationDto> observations) {}
