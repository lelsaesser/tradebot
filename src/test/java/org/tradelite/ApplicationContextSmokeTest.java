package org.tradelite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "tradebot.api.finnhub-key=test-key",
            "tradebot.api.coingecko-key=test-key",
            "tradebot.api.twelvedata-key=test-key"
        })
@ActiveProfiles("dev")
class ApplicationContextSmokeTest {

    @TempDir static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("smoke-test.db"));
        registry.add(
                "tradebot.telegram.local-sink-file",
                () -> tempDir.resolve("telegram.log").toString());
    }

    @Autowired private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }
}
