package org.tradelite.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

// SecretRedactingTurboFilter has no per-test state, but tests construct fresh filter instances
// rather than rely on the global LoggerContext. SAME_THREAD execution avoids any cross-talk if
// future tests reach for shared logger state (matches the approach used in RootErrorHandlerTest).
@Execution(ExecutionMode.SAME_THREAD)
class SecretRedactingTurboFilterTest {

    /**
     * A token-shaped string matching trufflehog's Telegram regex: 8–10 digits, colon, 35 chars of
     * {@code [A-Za-z0-9_-]}. Not a real Telegram token — shape only.
     */
    private static final String FAKE_TOKEN_BODY = "1234567890:AbCdEfGhIjKlMnOpQrStUvWxYz012345678";

    private SecretRedactingTurboFilter filter;
    private Logger sampleLogger;

    @BeforeEach
    void setUp() {
        filter = new SecretRedactingTurboFilter();
        filter.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        filter.start();
        sampleLogger = (Logger) LoggerFactory.getLogger("test.SampleLogger");
    }

    @Test
    void deniesEvent_whenFormatStringContainsToken() {
        FilterReply reply =
                filter.decide(
                        null,
                        sampleLogger,
                        Level.ERROR,
                        "Sending failed for url https://api.telegram.org/bot"
                                + FAKE_TOKEN_BODY
                                + "/sendMessage",
                        null,
                        null);

        assertThat(reply).isEqualTo(FilterReply.DENY);
    }

    @Test
    void deniesEvent_whenStringParamContainsToken() {
        FilterReply reply =
                filter.decide(
                        null,
                        sampleLogger,
                        Level.ERROR,
                        "Error sending message: {}",
                        new Object[] {
                            "400 Bad Request on POST request for "
                                    + "https://api.telegram.org/bot"
                                    + FAKE_TOKEN_BODY
                                    + "/sendMessage"
                        },
                        null);

        assertThat(reply).isEqualTo(FilterReply.DENY);
    }

    @Test
    void deniesEvent_whenNonStringParamToStringContainsToken() {
        // Param is an Object whose toString() includes the token (e.g., a wrapping exception passed
        // via {} interpolation).
        Object wrapping =
                new Object() {
                    @Override
                    public String toString() {
                        return "Wrapper{cause=https://api.telegram.org/bot"
                                + FAKE_TOKEN_BODY
                                + "/sendMessage}";
                    }
                };

        FilterReply reply =
                filter.decide(
                        null,
                        sampleLogger,
                        Level.ERROR,
                        "Failure: {}",
                        new Object[] {wrapping},
                        null);

        assertThat(reply).isEqualTo(FilterReply.DENY);
    }

    @Test
    void deniesEvent_whenThrowableMessageContainsToken() {
        RuntimeException ex =
                new RuntimeException(
                        "400 Bad Request on POST request for https://api.telegram.org/bot"
                                + FAKE_TOKEN_BODY
                                + "/sendMessage");

        FilterReply reply =
                filter.decide(null, sampleLogger, Level.ERROR, "Operation failed", null, ex);

        assertThat(reply).isEqualTo(FilterReply.DENY);
    }

    @Test
    void deniesEvent_whenNestedCauseMessageContainsToken() {
        RuntimeException root =
                new RuntimeException(
                        "POST https://api.telegram.org/bot" + FAKE_TOKEN_BODY + "/sendMessage");
        RuntimeException middle = new RuntimeException("intermediate wrap", root);
        RuntimeException top = new RuntimeException("top-level message", middle);

        FilterReply reply =
                filter.decide(
                        null,
                        sampleLogger,
                        Level.ERROR,
                        "Send failed: {}",
                        new Object[] {top},
                        top);

        assertThat(reply).isEqualTo(FilterReply.DENY);
    }

    @Test
    void survivesCausalCycle_returnsWithoutInfiniteLoop() {
        // Build a synthetic causal cycle: a -> b -> a. Logback should never see this in practice,
        // but reflective frameworks have produced cycles before. Filter must terminate.
        RuntimeException a = new RuntimeException("alpha");
        RuntimeException b = new RuntimeException("beta", a);
        a.initCause(b);

        // No token in either message → expect NEUTRAL (and, critically, no hang).
        FilterReply reply = filter.decide(null, sampleLogger, Level.ERROR, "cycle test", null, a);

        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void neutralReply_whenNoSecretAnywhere() {
        FilterReply reply =
                filter.decide(
                        null,
                        sampleLogger,
                        Level.INFO,
                        "Normal log line about ticker {}",
                        new Object[] {"AAPL"},
                        null);

        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void neutralReply_whenAllInputsAreNull() {
        FilterReply reply = filter.decide(null, sampleLogger, Level.INFO, null, null, null);

        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void neutralReply_whenParamsContainNullElements() {
        FilterReply reply =
                filter.decide(
                        null,
                        sampleLogger,
                        Level.INFO,
                        "Mixed: {} {}",
                        new Object[] {null, "AAPL"},
                        null);

        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void neutralReply_whenEventCarriesBypassMarker() {
        // Events tagged with the BYPASS_MARKER must pass through untouched even if they contain a
        // token-shaped string. This is the recursion-guard contract: the filter's own breadcrumb
        // emissions must not be re-evaluated by the filter.
        FilterReply reply =
                filter.decide(
                        SecretRedactingTurboFilter.BYPASS_MARKER,
                        sampleLogger,
                        Level.WARN,
                        "Suppressed log event with token " + FAKE_TOKEN_BODY,
                        null,
                        null);

        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void neutralReply_whenFilterIsNotStarted() {
        SecretRedactingTurboFilter notStarted = new SecretRedactingTurboFilter();
        // Deliberately do NOT call start(). An unconfigured filter must not unilaterally drop
        // events.
        FilterReply reply =
                notStarted.decide(
                        null,
                        sampleLogger,
                        Level.ERROR,
                        "Token here: " + FAKE_TOKEN_BODY,
                        null,
                        null);

        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    /**
     * Regression test for issue #464. Exact log shape that produced the leak: a {@code
     * RestTemplate} {@code 400 Bad Request} exception whose message embeds the bot URL with the
     * token in the path. The fixed filter must drop this event so it never reaches journald.
     */
    @Test
    void deniesRegressionShapeFromIssue464() {
        String regressionMessage =
                "400 Bad Request on POST request for "
                        + "\"https://api.telegram.org/bot"
                        + FAKE_TOKEN_BODY
                        + "/sendMessage\": \"...\"";
        Throwable cause = new RuntimeException(regressionMessage);

        FilterReply replyViaParams =
                filter.decide(
                        null,
                        sampleLogger,
                        Level.ERROR,
                        "Error sending message: {}",
                        new Object[] {cause.getMessage()},
                        null);

        FilterReply replyViaThrowable =
                filter.decide(
                        null, sampleLogger, Level.ERROR, "Error sending message", null, cause);

        assertThat(replyViaParams).isEqualTo(FilterReply.DENY);
        assertThat(replyViaThrowable).isEqualTo(FilterReply.DENY);
    }

    @Test
    void marker_doesNotMatchUnrelatedMarker() {
        // Sanity: an unrelated marker must not be confused with BYPASS_MARKER.
        FilterReply reply =
                filter.decide(
                        MarkerFactory.getMarker("UNRELATED"),
                        sampleLogger,
                        Level.ERROR,
                        "Token: " + FAKE_TOKEN_BODY,
                        null,
                        null);

        assertThat(reply).isEqualTo(FilterReply.DENY);
    }
}
