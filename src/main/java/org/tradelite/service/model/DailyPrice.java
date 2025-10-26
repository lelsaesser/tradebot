package org.tradelite.service.model;

import java.time.LocalDate;
import lombok.Data;

@Data
public class DailyPrice {
    private LocalDate date;
    private double price;
}
