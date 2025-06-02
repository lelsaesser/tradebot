package org.tradelite.common;


import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class TargetPrice {

    String symbol;
    double buyTarget;
    double sellTarget;
}
