package org.tradelite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;

// Tests share the RootErrorHandler logger via ListAppender; serialize within this class
// so concurrent tests do not leak log events into each other's appenders.
@Execution(ExecutionMode.SAME_THREAD)
class RootErrorHandlerTest {

    private RootErrorHandler rootErrorHandler;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        rootErrorHandler = new RootErrorHandler();

        logger = (Logger) LoggerFactory.getLogger(RootErrorHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        // Clear interrupt flag in case a test set it.
        Thread.interrupted();
    }

    @Test
    void testRun_withInterruptedException() {
        boolean success =
                rootErrorHandler.runWithStatus(
                        () -> {
                            throw new InterruptedException("interrupt detail");
                        });

        assertFalse(success);
        // Re-interrupt: handler must propagate the interrupt status to the calling thread.
        assertTrue(Thread.interrupted());

        assertThat(logAppender.list)
                .anySatisfy(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                            assertThat(event.getFormattedMessage())
                                    .contains("Operation was interrupted")
                                    .contains("interrupt detail");
                        });
    }

    @Test
    void testRun_withException() {
        boolean success =
                rootErrorHandler.runWithStatus(
                        () -> {
                            throw new RuntimeException("Test exception");
                        });

        assertFalse(success);
        assertThat(logAppender.list)
                .anySatisfy(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                            assertThat(event.getFormattedMessage()).contains("Test exception");
                            assertThat(event.getThrowableProxy()).isNotNull();
                        });
    }

    @Test
    void testRun_withNoException() {
        boolean success =
                rootErrorHandler.runWithStatus(
                        () -> {
                            // No exception thrown
                        });

        assertTrue(success);
        assertThat(logAppender.list).isEmpty();
    }

    @Test
    void run_delegatesToStatusPath() {
        rootErrorHandler.run(
                () -> {
                    throw new RuntimeException("delegated");
                });

        assertThat(logAppender.list)
                .anySatisfy(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                            assertThat(event.getFormattedMessage()).contains("delegated");
                        });
    }
}
