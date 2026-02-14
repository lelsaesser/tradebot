package org.tradelite.client.telegram;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.telegram.dto.TelegramSendMessageResponse;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.client.telegram.dto.TelegramUpdateResponseWrapper;
import org.tradelite.config.TradebotTelegramProperties;

@Slf4j
@Component
@Profile("prod")
public class TelegramClient implements TelegramGateway {

    protected static final String BASE_URL = "https://api.telegram.org/bot%s/sendMessage";
    protected static final String DELETE_URL = "https://api.telegram.org/bot%s/deleteMessage";

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
    public OptionalLong sendMessageAndReturnId(String message) {
        String url = String.format(BASE_URL, botToken);

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", groupChatId);
        payload.put("text", message);
        payload.put("parse_mode", "Markdown");

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
            log.error("Error sending message: {}", e.getMessage());
            return OptionalLong.empty();
        }
    }

    /**
     * Deletes a message from the group chat by its message ID. A bot can always delete its own
     * messages.
     */
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
            log.warn("Error deleting message {}: {}", messageId, e.getMessage());
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
            log.error("Error fetching chat updates: {}", e.getMessage());
            throw new IllegalStateException("Error fetching chat updates: " + e.getMessage());
        }
    }
}
