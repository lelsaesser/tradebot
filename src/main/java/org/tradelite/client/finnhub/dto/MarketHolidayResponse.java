package org.tradelite.client.finnhub.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MarketHolidayResponse {

    private List<MarketHoliday> data;
    private String exchange;
    private String timezone;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MarketHoliday {
        private String eventName;
        private String atDate;
        private String tradingHour;
    }
}
