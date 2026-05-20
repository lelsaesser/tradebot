package org.tradelite.client.telegram;

/**
 * Sanitizes outgoing Telegram messages to fit within Telegram's hard 4096-char limit.
 *
 * <p>Strategy: if the message is over the limit, first strip legacy-Markdown markers ({@code *},
 * {@code _}, {@code `}); if that brings it under the limit, deliver the full report unformatted.
 * Otherwise truncate at the last newline within the limit (or hard-cut at 4093 if no newline exists
 * in the window). Truncated/stripped payloads must be sent without {@code parse_mode} since the
 * formatting markers are gone.
 */
public final class TelegramMessageSanitizer {

    public static final int LIMIT = 4096;
    static final int HARD_CUT_FALLBACK = 4093;

    private TelegramMessageSanitizer() {}

    public enum Outcome {
        UNCHANGED,
        STRIPPED_ONLY,
        TRUNCATED
    }

    public record SanitizeResult(String payload, Outcome outcome, int originalLength) {}

    public static SanitizeResult sanitize(String message) {
        int originalLength = message.length();

        if (originalLength <= LIMIT) {
            return new SanitizeResult(message, Outcome.UNCHANGED, originalLength);
        }

        String stripped = message.replaceAll("[*_`]", "");

        if (stripped.length() <= LIMIT) {
            return new SanitizeResult(stripped, Outcome.STRIPPED_ONLY, originalLength);
        }

        String window = stripped.substring(0, LIMIT);
        int lastNewline = window.lastIndexOf('\n');

        String truncated;
        if (lastNewline >= 0) {
            truncated = stripped.substring(0, lastNewline);
        } else {
            truncated = stripped.substring(0, HARD_CUT_FALLBACK);
        }

        return new SanitizeResult(truncated, Outcome.TRUNCATED, originalLength);
    }
}
