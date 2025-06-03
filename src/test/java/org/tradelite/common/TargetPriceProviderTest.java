package org.tradelite.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

@ExtendWith(MockitoExtension.class)
class TargetPriceProviderTest {

    private static final String FILE_PATH = "config/target-prices-test.json";

    private TargetPriceProvider targetPriceProvider;

    @BeforeEach
    void setUp() {
        targetPriceProvider = new TargetPriceProvider(new ObjectMapper());
    }

    @Test
    void updateTargetPrice() {
        targetPriceProvider.updateTargetPrice(CoinId.SOLANA, 160.0, 200.0, FILE_PATH);
        List<TargetPrice> targetPrices = targetPriceProvider.loadTargetPrices(FILE_PATH);

        for (TargetPrice targetPrice : targetPrices) {
            if (targetPrice.getSymbol().equals(CoinId.SOLANA.getName())) {
                assertThat(targetPrice.getBuyTarget(), is(160.0));
                assertThat(targetPrice.getSellTarget(), is(200.0));
            }
        }

        targetPriceProvider.updateTargetPrice(CoinId.SOLANA, 250.0, 1100.0, FILE_PATH);
        targetPrices = targetPriceProvider.loadTargetPrices(FILE_PATH);

        for (TargetPrice targetPrice : targetPrices) {
            if (targetPrice.getSymbol().equals(CoinId.SOLANA.getName())) {
                assertThat(targetPrice.getBuyTarget(), is(250.0));
                assertThat(targetPrice.getSellTarget(), is(1100.0));
            }
        }
    }
}
