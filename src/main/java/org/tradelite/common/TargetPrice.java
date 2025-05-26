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

    String symbol;
    double targetPriceBuy;
    double targetPriceSell;
}
