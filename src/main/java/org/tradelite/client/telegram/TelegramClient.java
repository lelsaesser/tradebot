package org.tradelite.client.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.client.telegram.dto.TelegramUpdateResponseWrapper;

import java.util.List;

@Slf4j
@Component
public class TelegramClient {

    protected static final String BASE_URL = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";

    private final RestTemplate restTemplate;
    private final String botToken;
    private final String groupChatId;

    @Autowired
    public TelegramClient(
            RestTemplate restTemplate,
            @Value("${TELEGRAM_BOT_TOKEN}") String botToken,
            @Value("${TELEGRAM_BOT_GROUP_CHAT_ID}") String groupChatId) {
        this.restTemplate = restTemplate;
        this.botToken = botToken;
        this.groupChatId = groupChatId;

    }

    public void sendMessage(String message) {
        String url = String.format(BASE_URL, botToken, groupChatId, message);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.info("bot url: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
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
        String url = String.format("https://api.telegram.org/bot%s/getUpdates", botToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.info("Fetching chat updates");
            ResponseEntity<TelegramUpdateResponseWrapper> response = restTemplate.exchange(url, HttpMethod.GET, entity, TelegramUpdateResponseWrapper.class);
            return response.getBody().getResult();
        } catch (Exception e) {
            log.error("Error fetching chat updates: {}", e.getMessage());
            throw new IllegalStateException("Error fetching chat updates: " + e.getMessage());
        }
    }

}
