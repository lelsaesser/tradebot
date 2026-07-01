package org.tradelite.common;

/**
 * Hook for components that own ticker-keyed state and need to clean it up when a symbol is removed
 * from tracking.
 *
 * <p>Implementations are auto-discovered as Spring beans and invoked by {@code
 * SymbolManagementService.removeSymbol(ticker)} after the registry and target-price cleanup.
 * Fan-out is unordered and fail-isolated — a single listener throwing is logged at WARN and does
 * not block the remaining listeners or change the user-visible outcome.
 *
 * <p>Listeners must be idempotent: a call for a ticker that was never tracked (or a duplicate call)
 * must succeed silently with no side effects beyond the DELETE itself. The natural atomicity of a
 * single-statement SQL DELETE plus SymbolRegistry's "return true exactly once" semantics for the
 * remove operation give us the "fires once per real remove" guarantee without explicit
 * synchronization.
 *
 * <p>Documented inconsistency: {@code target_prices} and {@code tracked_symbols} cleanup continues
 * to run via the legacy direct-call path in {@code SymbolManagementService} (those calls predate
 * this interface and have non-void return contracts). Every other ticker-keyed table is cleaned via
 * a listener.
 */
public interface SymbolLifecycleListener {

    /**
     * Invoked once per ticker after the symbol has been successfully removed from the registry.
     * Implementations should delete any ticker-keyed rows they own.
     *
     * @param ticker uppercase ticker that was removed
     */
    void onSymbolRemoved(String ticker);
}
