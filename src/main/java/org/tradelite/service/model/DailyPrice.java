package org.tradelite.service.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DailyPrice {
    private LocalDate date;
    private double price;
}
