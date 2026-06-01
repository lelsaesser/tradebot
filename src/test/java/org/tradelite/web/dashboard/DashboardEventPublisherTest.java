package org.tradelite.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class DashboardEventPublisherTest {

    private DashboardEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DashboardEventPublisher();
    }

    @Test
    void publish_sendsToRegisteredEmitter() throws IOException {
        SseEmitter emitter = spy(new SseEmitter(0L));
        publisher.register(emitter);

        publisher.publish("test-event", Map.of("key", "val"));

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void publish_fansOutToAllRegisteredEmitters() throws IOException {
        SseEmitter first = spy(new SseEmitter(0L));
        SseEmitter second = spy(new SseEmitter(0L));
        publisher.register(first);
        publisher.register(second);

        publisher.publish("ping", null);

        verify(first, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(second, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void publish_removesDeadEmitterAndStillSendsToHealthyOne() throws IOException {
        SseEmitter dead = spy(new SseEmitter(0L));
        SseEmitter alive = spy(new SseEmitter(0L));
        doThrow(new IOException("broken pipe"))
                .when(dead)
                .send(any(SseEmitter.SseEventBuilder.class));
        publisher.register(dead);
        publisher.register(alive);

        publisher.publish("ping", null);

        verify(alive, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void publish_sendsCorrectEventTypeAndPayload() throws IOException {
        SseEmitter emitter = spy(new SseEmitter(0L));
        publisher.register(emitter);

        publisher.publish("test-event", Map.of("key", "val"));

        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(captor.capture());
        Set<ResponseBodyEmitter.DataWithMediaType> items = captor.getValue().build();
        Optional<DashboardEvent> event =
                items.stream()
                        .map(ResponseBodyEmitter.DataWithMediaType::getData)
                        .filter(DashboardEvent.class::isInstance)
                        .map(DashboardEvent.class::cast)
                        .findFirst();
        assertThat(event).isPresent();
        assertThat(event.get().type()).isEqualTo("test-event");
        assertThat(event.get().payload()).isEqualTo(Map.of("key", "val"));
    }

    @Test
    void heartbeat_sendsToRegisteredEmitter() throws IOException {
        SseEmitter emitter = spy(new SseEmitter(0L));
        publisher.register(emitter);

        publisher.heartbeat();

        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(captor.capture());
        Set<ResponseBodyEmitter.DataWithMediaType> items = captor.getValue().build();
        Optional<DashboardEvent> event =
                items.stream()
                        .map(ResponseBodyEmitter.DataWithMediaType::getData)
                        .filter(DashboardEvent.class::isInstance)
                        .map(DashboardEvent.class::cast)
                        .findFirst();
        assertThat(event).isPresent();
        assertThat(event.get().type()).isEqualTo("ping");
        assertThat(event.get().payload()).isNull();
    }

    @Test
    void publish_deadEmitterNotCalledOnSecondPublish() throws IOException {
        SseEmitter dead = spy(new SseEmitter(0L));
        SseEmitter alive = spy(new SseEmitter(0L));
        doThrow(new IOException("broken pipe"))
                .when(dead)
                .send(any(SseEmitter.SseEventBuilder.class));
        publisher.register(dead);
        publisher.register(alive);

        publisher.publish("first", null);
        publisher.publish("second", null);

        // dead removed after first publish — not called on second
        verify(dead, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(alive, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
