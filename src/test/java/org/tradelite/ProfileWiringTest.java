package org.tradelite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.telegram.LocalTelegramGateway;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.config.TradebotTelegramProperties;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.repository.MomentumRocRepository;
import org.tradelite.repository.OhlcvRepository;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RsiService;

class ProfileWiringTest {

    private final ApplicationContextRunner telegramContextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                    .withUserConfiguration(
                            TelegramGatewayTestConfig.class,
                            TelegramClient.class,
                            LocalTelegramGateway.class)
                    .withPropertyValues(
                            "tradebot.telegram.bot-token=test-token",
                            "tradebot.telegram.group-chat-id=test-chat",
                            "tradebot.telegram.local-sink-file=target/test-dev-telegram.log");

    private final ApplicationContextRunner devSeederContextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(DevSeederTestConfig.class, DevDataSeeder.class)
                    .withPropertyValues("spring.profiles.active=dev");

    @Test
    void defaultProfileUsesRealTelegramGateway() {
        telegramContextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(TelegramGateway.class);
                    assertThat(context).hasSingleBean(TelegramClient.class);
                    assertThat(context).doesNotHaveBean(LocalTelegramGateway.class);
                });
    }

    @Test
    void devProfileUsesLocalTelegramGateway() {
        telegramContextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(
                        context -> {
                            assertThat(context).hasBean("localTelegramGateway");
                            assertThat(context).hasBean("telegramClient");
                            assertThat(context.getBean(TelegramGateway.class))
                                    .isInstanceOf(LocalTelegramGateway.class);
                        });
    }

    @Test
    void devProfileInstantiatesDevDataSeederBean() {
        devSeederContextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(DevDataSeeder.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TradebotTelegramProperties.class)
    static class TelegramGatewayTestConfig {

        @Bean
        RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DevSeederTestConfig {

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MomentumRocRepository momentumRocRepository() {
            return mock(MomentumRocRepository.class);
        }

        @Bean
        PriceQuoteRepository priceQuoteRepository() {
            return mock(PriceQuoteRepository.class);
        }

        @Bean
        RsiService rsiService() {
            return mock(RsiService.class);
        }

        @Bean
        RelativeStrengthService relativeStrengthService() {
            return mock(RelativeStrengthService.class);
        }

        @Bean
        TargetPriceProvider targetPriceProvider() {
            return mock(TargetPriceProvider.class);
        }

        @Bean
        SymbolRegistry stockSymbolRegistry() {
            return mock(SymbolRegistry.class);
        }

        @Bean
        OhlcvRepository ohlcvRepository() {
            return mock(OhlcvRepository.class);
        }

        @Bean
        FinnhubPriceEvaluator finnhubPriceEvaluator() {
            return mock(FinnhubPriceEvaluator.class);
        }
    }
}
