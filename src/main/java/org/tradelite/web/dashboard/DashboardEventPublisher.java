package org.tradelite.web.dashboard;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class DashboardEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DashboardEventPublisher.class);

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    void register(SseEmitter emitter) {
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
    }

    public void publish(String eventType, Object payload) {
        DashboardEvent event = DashboardEvent.of(eventType, payload);
        SseEmitter.SseEventBuilder builder = SseEmitter.event().name(eventType).data(event);
        emitters.forEach(emitter -> {
            try {
                emitter.send(builder);
            } catch (IOException e) {
                log.debug("SSE emitter dead during publish, removing: {}", e.getMessage());
                emitter.complete();
                emitters.remove(emitter);
            }
        });
    }

    @Scheduled(fixedRate = 30_000)
    void heartbeat() {
        publish("ping", null);
    }
}
