package org.tradelite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RootErrorHandler {

    public void run(ThrowingRunnable body) {
        runWithStatus(body);
    }

    public boolean runWithStatus(ThrowingRunnable body) {
        try {
            body.run();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Operation was interrupted: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }
}
