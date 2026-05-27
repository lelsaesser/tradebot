package org.tradelite.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseControllerTest {

    @Test
    void events_registersEmitterWithPublisherAndReturnsIt() {
        DashboardEventPublisher publisher = mock(DashboardEventPublisher.class);
        SseController controller = new SseController(publisher);

        SseEmitter emitter = controller.events();

        assertThat(emitter).isNotNull();
        verify(publisher).register(emitter);
    }
}
