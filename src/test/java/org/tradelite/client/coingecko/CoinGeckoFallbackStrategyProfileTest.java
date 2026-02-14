package org.tradelite.client.coingecko;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tradelite.config.TradebotApiProperties;

class CoinGeckoFallbackStrategyProfileTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            CoinGeckoSupportConfig.class,
                            NoFallbackCoinGeckoStrategy.class,
                            FixtureFallbackCoinGeckoStrategy.class);

    @Test
    void devProfile_usesFixtureFallbackStrategy() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(
                        context ->
                                assertThat(context.getBean(CoinGeckoFallbackStrategy.class))
                                        .isInstanceOf(FixtureFallbackCoinGeckoStrategy.class));
    }

    @Test
    void nonDevProfile_usesNoFallbackStrategy() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(
                        context ->
                                assertThat(context.getBean(CoinGeckoFallbackStrategy.class))
                                        .isInstanceOf(NoFallbackCoinGeckoStrategy.class));
    }

    @Configuration
    static class CoinGeckoSupportConfig {
        @Bean
        TradebotApiProperties tradebotApiProperties() {
            return new TradebotApiProperties();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
