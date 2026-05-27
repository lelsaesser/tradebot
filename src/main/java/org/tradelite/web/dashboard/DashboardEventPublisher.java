package org.tradelite.web.dashboard;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class DashboardEventPublisher {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
    }

    public void publish(String eventType, Object payload) {
        DashboardEvent event = DashboardEvent.of(eventType, payload);
        SseEmitter.SseEventBuilder builder = SseEmitter.event().name(eventType).data(event);
        emitters.forEach(
                emitter -> {
                    try {
                        emitter.send(builder);
                    } catch (IOException e) {
                        log.warn("SSE client gone, removing emitter: {}", e.getMessage());
                        emitter.complete();
                        emitters.remove(emitter);
                    } catch (Exception e) {
                        log.error("Unexpected SSE send failure, removing emitter", e);
                        emitter.complete();
                        emitters.remove(emitter);
                    }
                });
    }

    @Scheduled(fixedRate = 30_000, scheduler = "dashboardHeartbeatScheduler")
    void heartbeat() {
        if (emitters.isEmpty()) return;
        publish("ping", null);
    }
}
