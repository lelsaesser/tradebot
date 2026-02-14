package org.tradelite.client.finnhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tradelite.config.TradebotApiProperties;

class FinnhubFallbackStrategyProfileTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(
                    FinnhubSupportConfig.class,
                    NoFallbackFinnhubStrategy.class,
                    FixtureFallbackFinnhubStrategy.class);

    @Test
    void devProfile_usesFixtureFallbackStrategy() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(
                        context ->
                                assertThat(context.getBean(FinnhubFallbackStrategy.class))
                                        .isInstanceOf(FixtureFallbackFinnhubStrategy.class));
    }

    @Test
    void nonDevProfile_usesNoFallbackStrategy() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(
                        context ->
                                assertThat(context.getBean(FinnhubFallbackStrategy.class))
                                        .isInstanceOf(NoFallbackFinnhubStrategy.class));
    }

    @Configuration
    static class FinnhubSupportConfig {
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
