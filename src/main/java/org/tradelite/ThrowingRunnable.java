package org.tradelite;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}
