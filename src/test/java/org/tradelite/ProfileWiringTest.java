package org.tradelite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;
import org.sqlite.SQLiteDataSource;
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
        DataSource dataSource() {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl("jdbc:sqlite:file::memory:?cache=shared");
            return ds;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            JdbcTemplate jt = new JdbcTemplate(dataSource);
            jt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS finnhub_price_quotes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        symbol TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        current_price REAL NOT NULL,
                        daily_open REAL, daily_high REAL, daily_low REAL,
                        change_amount REAL, change_percent REAL, previous_close REAL,
                        UNIQUE(symbol, timestamp)
                    )
                    """);
            jt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS momentum_roc_state (
                        symbol TEXT PRIMARY KEY,
                        previous_roc10 REAL NOT NULL,
                        previous_roc20 REAL NOT NULL,
                        initialized INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            jt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS twelvedata_daily_ohlcv (
                        symbol TEXT NOT NULL, date TEXT NOT NULL,
                        open REAL, high REAL, low REAL, close REAL, volume INTEGER,
                        UNIQUE(symbol, date)
                    )
                    """);
            return jt;
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
