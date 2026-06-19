package org.tradelite.client.enrico.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Minimal projection of an Enrico holiday entry. The wire format also carries {@code holidayType}
 * and {@code flags} fields that we don't need — {@code holidayType} is filtered to {@code
 * public_holiday} via the URL query parameter, so every row we receive is already the right type.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} future-proofs against Enrico adding new
 * fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnricoHolidayDto(
        @JsonProperty("date") DateDto date, @JsonProperty("name") List<NameDto> name) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DateDto(int day, int month, int year) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NameDto(String lang, String text) {}
}
