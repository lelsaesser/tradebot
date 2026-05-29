package org.tradelite.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Logback {@link TurboFilter} that drops any log event whose format string, parameters, or
 * throwable cause-chain message matches a known secret shape. Fail-closed by design: better to lose
 * a diagnostic line than to leak a credential to disk, journald, or a monitoring backend.
 *
 * <h2>Detected secret shapes</h2>
 *
 * <p>Currently detects Telegram bot tokens (the leak class behind issues #456 / #464). Pattern
 * matches trufflehog's industry-standard regex: 8–10 digits, colon, 35 chars of {@code
 * [A-Za-z0-9_-]}. Add additional patterns to {@link #SECRET_PATTERNS} as new secret shapes are
 * onboarded.
 *
 * <h2>Inputs scanned</h2>
 *
 * <ul>
 *   <li>{@code format} string
 *   <li>each element of {@code params}, coerced via {@link String#valueOf(Object)}
 *   <li>{@code Throwable t} message + recursive cause chain (cycle-safe via identity tracking)
 * </ul>
 *
 * <p><b>Known blind spot (accepted residual risk):</b> rendered stack frames. TurboFilter runs
 * before event creation, so frame info has not yet been materialized. Throwable.getMessage() on
 * each cause covers the path that produced #464 ({@code RestTemplate} exception messages embedding
 * the token in a URL), which is the only known concrete leak vector in this codebase.
 *
 * <h2>Drop behavior</h2>
 *
 * <p>On match: returns {@link FilterReply#DENY} (drops the event entirely) and emits a sanitized
 * breadcrumb at WARN — the breadcrumb names the originating logger and level but never the matched
 * content. The breadcrumb is tagged with {@link #BYPASS_MARKER} so it bypasses this filter on its
 * way to the appender (no recursion).
 *
 * <p>On no match: returns {@link FilterReply#NEUTRAL} (lets normal level checks decide).
 */
public class SecretRedactingTurboFilter extends TurboFilter {

    /** Logger used to emit the sanitized breadcrumb when an event is dropped. */
    private static final org.slf4j.Logger BREADCRUMB_LOG =
            org.slf4j.LoggerFactory.getLogger(SecretRedactingTurboFilter.class);

    /**
     * Marker on breadcrumb log events; the filter ignores events tagged with it (recursion guard).
     */
    static final Marker BYPASS_MARKER = MarkerFactory.getMarker("SECRET_FILTER_BYPASS");

    /**
     * Patterns considered to be secrets. Compiled once. Each pattern's literal-shape requirement
     * (digits + colon + 35 specific chars) is specific enough that false positives on real text are
     * negligible.
     *
     * <p>Note: no {@code \b} anchors. Word boundaries fail when the token is embedded after another
     * word character (e.g., {@code bot1234567890:...} in a URL has no boundary between {@code t}
     * and {@code 1}). The shape itself is the anchor.
     */
    static final List<Pattern> SECRET_PATTERNS =
            List.of(
                    // Telegram bot token: trufflehog's industry-standard shape
                    // (8–10 digits, colon, 35 chars of [A-Za-z0-9_-]).
                    Pattern.compile("\\d{8,10}:[A-Za-z0-9_-]{35}"));

    @Override
    public void start() {
        // Filter has no configuration to validate. Mark started so decide() does not short-circuit
        // to NEUTRAL.
        super.start();
    }

    @Override
    public FilterReply decide(
            Marker marker,
            Logger logger,
            Level level,
            String format,
            Object[] params,
            Throwable t) {

        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }

        // Recursion guard: events emitted by the filter itself (the breadcrumb) carry this marker
        // and must pass through untouched.
        if (marker != null && marker.contains(BYPASS_MARKER)) {
            return FilterReply.NEUTRAL;
        }

        if (containsSecret(format) || paramsContainSecret(params) || throwableContainsSecret(t)) {
            emitBreadcrumb(logger, level);
            return FilterReply.DENY;
        }

        return FilterReply.NEUTRAL;
    }

    private static boolean containsSecret(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (Pattern p : SECRET_PATTERNS) {
            if (p.matcher(s).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean paramsContainSecret(Object[] params) {
        if (params == null) {
            return false;
        }
        for (Object p : params) {
            if (p == null) {
                continue;
            }
            // String.valueOf is null-safe and avoids the per-call cost of MessageFormatter.format,
            // which would render the full message string. Per-param scanning catches the same
            // tokens with lower overhead.
            if (containsSecret(String.valueOf(p))) {
                return true;
            }
        }
        return false;
    }

    private static boolean throwableContainsSecret(Throwable t) {
        if (t == null) {
            return false;
        }
        // Identity-based set guards against cause-chain cycles (rare but possible with reflective
        // frameworks that intern exception instances).
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cur = t;
        while (cur != null && seen.add(cur)) {
            if (containsSecret(cur.getMessage())) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static void emitBreadcrumb(Logger logger, Level level) {
        String loggerName = logger == null ? "<unknown>" : logger.getName();
        String levelName = level == null ? "<unknown>" : level.toString();
        // Tagged with BYPASS_MARKER so this very line is not re-scanned + dropped by us.
        BREADCRUMB_LOG.warn(
                BYPASS_MARKER,
                "Suppressed log event from {} at level {} containing potential secret",
                loggerName,
                levelName);
    }
}
