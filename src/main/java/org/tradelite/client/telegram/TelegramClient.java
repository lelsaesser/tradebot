package org.tradelite.client.telegram;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.telegram.TelegramMessageSanitizer.SanitizeResult;
import org.tradelite.client.telegram.dto.TelegramSendMessageResponse;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.client.telegram.dto.TelegramUpdateResponseWrapper;
import org.tradelite.config.TradebotTelegramProperties;
import org.tradelite.logging.SecretRedactingTurboFilter;

@Slf4j
@Component
public class TelegramClient implements TelegramGateway {

    protected static final String BASE_URL = "https://api.telegram.org/bot%s/sendMessage";
    protected static final String DELETE_URL = "https://api.telegram.org/bot%s/deleteMessage";

    /**
     * Matches the {@code /bot<TOKEN>/} URL path segment that Telegram requires for authentication
     * (see <a href="https://core.telegram.org/bots/api">Bot API docs</a>: the token must live in
     * the URL — there is no header- or body-based alternative). Used by {@link #redact(String)} to
     * scrub tokens from any string that might reach a logger or an exception message.
     *
     * <p>Targets the URL path shape rather than the bare token shape, since {@code RestTemplate}
     * exception messages always carry the token embedded in the URL. The trailing {@code /} anchors
     * the match to the path segment and avoids over-matching.
     */
    private static final Pattern TOKEN_URL_PATTERN = Pattern.compile("/bot[^/\\s]+/");

    private final RestTemplate restTemplate;
    private final String botToken;
    private final String groupChatId;

    @Autowired
    public TelegramClient(
            RestTemplate restTemplate, TradebotTelegramProperties telegramProperties) {
        this.restTemplate = restTemplate;
        this.botToken = telegramProperties.getBotToken();
        this.groupChatId = telegramProperties.getGroupChatId();
    }

    @Override
    public void sendMessage(String message) {
        sendMessageAndReturnId(message);
    }

    /**
     * Sends a message and returns the message ID of the sent message. Returns empty if the message
     * could not be sent or the response could not be parsed.
     */
    @Override
    public OptionalLong sendMessageAndReturnId(String message) {
        SanitizeResult sanitized = TelegramMessageSanitizer.sanitize(message);
        switch (sanitized.outcome()) {
            case STRIPPED_ONLY ->
                    log.warn(
                            "Telegram message {} chars, exceeded {} char limit; stripped markers and sent as plain text",
                            sanitized.originalLength(),
                            TelegramMessageSanitizer.LIMIT);
            case TRUNCATED ->
                    log.warn(
                            "Telegram message truncated from {} to {} chars; sent as plain text",
                            sanitized.originalLength(),
                            sanitized.payload().length());
            case UNCHANGED -> {
                /* no-op */
            }
        }

        String url = String.format(BASE_URL, botToken);

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", groupChatId);
        payload.put("text", sanitized.payload());
        if (sanitized.outcome() == TelegramMessageSanitizer.Outcome.UNCHANGED) {
            payload.put("parse_mode", "Markdown");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<TelegramSendMessageResponse> response =
                    restTemplate.exchange(
                            url, HttpMethod.POST, entity, TelegramSendMessageResponse.class);
            if (response.getStatusCode() == HttpStatus.OK
                    && response.getBody() != null
                    && response.getBody().isOk()
                    && response.getBody().getResult() != null) {
                log.info("Message sent successfully");
                return OptionalLong.of(response.getBody().getResult().getMessageId());
            } else {
                log.warn("Failed to send message: {}", response.getBody());
                return OptionalLong.empty();
            }
        } catch (Exception e) {
            log.error("Error sending message: {}", redact(e.getMessage()));
            return OptionalLong.empty();
        }
    }

    /**
     * Deletes a message from the group chat by its message ID. A bot can always delete its own
     * messages.
     */
    @Override
    public void deleteMessage(long messageId) {
        String url = String.format(DELETE_URL, botToken);

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", groupChatId);
        payload.put("message_id", messageId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Message {} deleted successfully", messageId);
            } else {
                log.warn("Failed to delete message {}: {}", messageId, response.getBody());
            }
        } catch (Exception e) {
            log.warn("Error deleting message {}: {}", messageId, redact(e.getMessage()));
        }
    }

    @Override
    public List<TelegramUpdateResponse> getChatUpdates() {
        String url = String.format("https://api.telegram.org/bot%s/getUpdates", botToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.info("Fetching chat updates");
            ResponseEntity<TelegramUpdateResponseWrapper> response =
                    restTemplate.exchange(
                            url, HttpMethod.GET, entity, TelegramUpdateResponseWrapper.class);
            return response.getBody().getResult();
        } catch (Exception e) {
            String safeMessage = redact(e.getMessage());
            log.error("Error fetching chat updates: {}", safeMessage);
            throw new IllegalStateException("Error while fetching chat updates: " + safeMessage);
        }
    }

    /**
     * Strips Telegram bot tokens from the {@code /bot<TOKEN>/} URL path segment that {@code
     * RestTemplate} exception messages embed when an HTTP call fails. Null-safe.
     *
     * <p>This is the primary defense — done at the source so the diagnostic log line stays readable
     * and the {@link SecretRedactingTurboFilter} (defense-in-depth) does not need to drop the
     * event. Without this, both the local {@code log.error} and any rethrown {@code
     * IllegalStateException} carrying the same message would propagate the token to every
     * downstream sink (journald, monitoring, GitHub paste).
     */
    static String redact(String s) {
        return s == null ? null : TOKEN_URL_PATTERN.matcher(s).replaceAll("/bot<redacted>/");
    }
}
