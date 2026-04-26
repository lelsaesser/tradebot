package org.tradelite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationTest {

    @Test
    void mainMethodSignature() throws NoSuchMethodException {
        Method mainMethod = Application.class.getMethod("main", String[].class);

        assertThat(mainMethod.getReturnType(), is(void.class));
        assertThat(Modifier.isPublic(mainMethod.getModifiers()), is(true));
        assertThat(Modifier.isStatic(mainMethod.getModifiers()), is(true));
    }

    @Test
    void constructor() {
        // Test the constructor to improve coverage
        Application application = new Application();
        assertThat(application, is(notNullValue()));
    }

    @Test
    void mainStartsApplicationInMinimalMode(@TempDir Path tempDir) {
        String[] args = {
            "--spring.profiles.active=dev",
            "--spring.main.web-application-type=none",
            "--spring.main.register-shutdown-hook=false",
            "--spring.datasource.url=jdbc:sqlite:" + tempDir.resolve("application-test.db"),
            "--tradebot.api.finnhub-key=test-finnhub-key",
            "--tradebot.api.coingecko-key=test-coingecko-key",
            "--tradebot.telegram.local-sink-file=" + tempDir.resolve("telegram.log"),
            "--metering.counter-dir=" + tempDir.resolve("counters")
        };

        assertDoesNotThrow(() -> Application.main(args));
    }
}
