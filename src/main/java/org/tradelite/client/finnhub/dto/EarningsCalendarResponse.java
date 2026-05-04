package org.tradelite.client.finnhub.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EarningsCalendarResponse {

    private List<EarningsEvent> earningsCalendar;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class EarningsEvent {
        private String date;
        private Double epsActual;
        private Double epsEstimate;
        private String hour;
        private int quarter;
        private Long revenueActual;
        private Long revenueEstimate;
        private String symbol;
        private int year;
    }
}
