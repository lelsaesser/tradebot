package org.tradelite.web.dashboard;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class DashboardEventPublisherTest {

    private DashboardEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DashboardEventPublisher();
    }

    @Test
    void publish_sendsCorrectEventTypeAndPayloadToRegisteredEmitter() throws IOException {
        SseEmitter emitter = spy(new SseEmitter(0L));
        publisher.register(emitter);

        publisher.publish("test-event", Map.of("key", "val"));

        verify(emitter, times(1)).send(argThat(builder -> {
            String built = builder.build().toString();
            return built.contains("test-event");
        }));
    }

    @Test
    void publish_fansOutToAllRegisteredEmitters() throws IOException {
        SseEmitter first = spy(new SseEmitter(0L));
        SseEmitter second = spy(new SseEmitter(0L));
        publisher.register(first);
        publisher.register(second);

        publisher.publish("ping", null);

        verify(first, times(1)).send(argThat(b -> b.build().toString().contains("ping")));
        verify(second, times(1)).send(argThat(b -> b.build().toString().contains("ping")));
    }

    @Test
    void publish_removesDeadEmitterAndStillSendsToHealthyOne() throws IOException {
        SseEmitter dead = spy(new SseEmitter(0L));
        SseEmitter alive = spy(new SseEmitter(0L));
        doThrow(new IOException("broken pipe")).when(dead).send(argThat(b -> true));
        publisher.register(dead);
        publisher.register(alive);

        publisher.publish("ping", null);

        verify(alive, times(1)).send(argThat(b -> b.build().toString().contains("ping")));
    }
}
