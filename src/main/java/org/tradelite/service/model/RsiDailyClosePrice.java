package org.tradelite.service.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
public class RsiDailyClosePrice {
    private List<DailyPrice> prices = new ArrayList<>();

    public void addPrice(LocalDate date, double price) {
        Optional<DailyPrice> existingPrice = prices.stream()
                .filter(p -> p.getDate().equals(date))
                .findFirst();

        if (existingPrice.isPresent()) {
            existingPrice.get().setPrice(price);
        } else {
            DailyPrice newPrice = new DailyPrice();
            newPrice.setDate(date);
            newPrice.setPrice(price);
            prices.add(newPrice);
        }

        if (prices.size() > 14) {
            prices.sort((p1, p2) -> p1.getDate().compareTo(p2.getDate()));
            prices.removeFirst();
        }
    }

    public List<Double> getPriceValues() {
        prices.sort((p1, p2) -> p1.getDate().compareTo(p2.getDate()));
        List<Double> priceValues = new ArrayList<>();
        for (DailyPrice price : prices) {
            priceValues.add(price.getPrice());
        }
        return priceValues;
    }
}
