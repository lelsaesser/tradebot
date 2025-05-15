package org.tradelite.common;


import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@EqualsAndHashCode
public class TargetPrice {

    StockTicker ticker;
    double targetPriceBuy;
    double targetPriceSell;
}
