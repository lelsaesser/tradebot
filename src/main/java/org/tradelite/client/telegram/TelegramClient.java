package org.tradelite.client.telegram;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.client.telegram.dto.TelegramUpdateResponseWrapper;

@Slf4j
@Component
public class TelegramClient {

    // Base URL configurable via properties (see application-stub.yaml for stub profile)
    private final RestTemplate restTemplate;
    private final String botToken;
    private final String groupChatId;
    private final String baseUrl;

    @Autowired
    public TelegramClient(
        RestTemplate restTemplate,
        @Value("${TELEGRAM_BOT_TOKEN}") String botToken,
        @Value("${TELEGRAM_BOT_GROUP_CHAT_ID}") String groupChatId,
        @Value("${telegram.base-url:https://api.telegram.org}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.botToken = botToken;
        this.groupChatId = groupChatId;
        this.baseUrl = baseUrl;
    }

    public void sendMessage(String message) {
        String url = String.format(
            "%s/bot%s/sendMessage?chat_id=%s&text=%s",
            baseUrl,
            botToken,
            groupChatId,
            message
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("parse_mode", "Markdown");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
            payload,
            headers
        );

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Message sent successfully");
            } else {
                log.warn("Failed to send message: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage());
        }
    }

    public List<TelegramUpdateResponse> getChatUpdates() {
        String url = String.format("%s/bot%s/getUpdates", baseUrl, botToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.info("Fetching chat updates");
            ResponseEntity<TelegramUpdateResponseWrapper> response =
                restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    TelegramUpdateResponseWrapper.class
                );
            return response.getBody().getResult();
        } catch (Exception e) {
            log.error("Error fetching chat updates: {}", e.getMessage());
            throw new IllegalStateException(
                "Error fetching chat updates: " + e.getMessage()
            );
        }
    }
}
