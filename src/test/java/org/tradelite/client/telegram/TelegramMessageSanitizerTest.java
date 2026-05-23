package org.tradelite.client.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tradelite.client.telegram.TelegramMessageSanitizer.LIMIT;

import org.junit.jupiter.api.Test;
import org.tradelite.client.telegram.TelegramMessageSanitizer.Outcome;
import org.tradelite.client.telegram.TelegramMessageSanitizer.SanitizeResult;

class TelegramMessageSanitizerTest {

    @Test
    void sanitize_underLimit_returnsUnchanged() {
        String message = "*Hello* _world_ `code` — this stays formatted.";

        SanitizeResult result = TelegramMessageSanitizer.sanitize(message);

        assertEquals(Outcome.UNCHANGED, result.outcome());
        assertEquals(message, result.payload());
        assertEquals(message.length(), result.originalLength());
    }

    @Test
    void sanitize_overLimitButFitsAfterStrip_returnsStrippedOnly() {
        // Build a message that is over the limit, but fits under it after stripping markers.
        // Strategy: pad with plain content close to the limit, then add a chunk of pure-marker
        // content that pushes us over but is fully removed by stripping.
        String plain = "x".repeat(LIMIT - 100); // well under limit
        String markers = "*".repeat(200); // pushes over limit, all removed by strip
        String message = plain + markers;
        assertTrue(message.length() > LIMIT, "precondition: original must exceed limit");
        assertTrue(
                message.replaceAll("[*_`]", "").length() <= LIMIT,
                "precondition: stripped length must fit");

        SanitizeResult result = TelegramMessageSanitizer.sanitize(message);

        assertEquals(Outcome.STRIPPED_ONLY, result.outcome());
        assertEquals(message.length(), result.originalLength());
        assertTrue(result.payload().length() <= LIMIT);
        assertFalse(result.payload().contains("*"));
        assertFalse(result.payload().contains("_"));
        assertFalse(result.payload().contains("`"));
    }

    @Test
    void sanitize_overLimitAfterStripWithNewlines_truncatesAtLastNewline() {
        // No markdown markers, plenty of newlines, well over the limit.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("row-").append(i).append("\n");
        }
        String message = sb.toString();
        assertTrue(message.length() > LIMIT);

        SanitizeResult result = TelegramMessageSanitizer.sanitize(message);

        assertEquals(Outcome.TRUNCATED, result.outcome());
        assertEquals(message.length(), result.originalLength());
        assertTrue(result.payload().length() <= LIMIT);
        // Cut happens at a newline boundary, so payload must end at a row terminator (no trailing
        // partial row). Verify the last char is not a newline (we cut everything including the
        // chosen newline) and the next char in the original was a newline.
        assertFalse(result.payload().endsWith("\n"));
        assertEquals('\n', message.charAt(result.payload().length()));
    }

    @Test
    void sanitize_overLimitAfterStripNoNewlinesInWindow_hardCutsAt4093() {
        // Single huge line, no newlines.
        String message = "x".repeat(LIMIT * 2);

        SanitizeResult result = TelegramMessageSanitizer.sanitize(message);

        assertEquals(Outcome.TRUNCATED, result.outcome());
        assertEquals(message.length(), result.originalLength());
        assertEquals(TelegramMessageSanitizer.HARD_CUT_FALLBACK, result.payload().length());
    }
}
