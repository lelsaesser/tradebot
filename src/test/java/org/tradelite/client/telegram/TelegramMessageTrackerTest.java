package org.tradelite.client.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class TelegramMessageTrackerTest {

    private TelegramMessageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TelegramMessageTracker();
    }

    @Test
    void getLastProcessedMessageId_success() {
        long messageId = tracker.getLastProcessedMessageId();

        assertThat(messageId, any(long.class));
        assertThat(messageId, greaterThan(0L));
    }

    @Test
    void setLastProcessedMessageId_success() {
        long currentMessageId = tracker.getLastProcessedMessageId();

        assertThat(currentMessageId, any(long.class));
        assertThat(currentMessageId, greaterThan(0L));

        long newMessageId = 123456789L;
        tracker.setLastProcessedMessageId(newMessageId);

        long lastProcessedMessageId = tracker.getLastProcessedMessageId();
        assertThat(lastProcessedMessageId, is(newMessageId));

        // Reset to initial state
        tracker.setLastProcessedMessageId(currentMessageId);
    }
}
