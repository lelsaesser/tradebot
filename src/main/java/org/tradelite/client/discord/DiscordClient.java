package org.tradelite.client.discord;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.discord.dto.DiscordMessageResponse;

@Slf4j
@Component
public class DiscordClient {

    protected static final String BASE_URL = "https://discord.com/api/v10/channels/%s/messages";

    private final RestTemplate restTemplate;
    private final String botToken;
    private final String channelId;

    @Autowired
    public DiscordClient(
            RestTemplate restTemplate,
            @Value("${DISCORD_BOT_TOKEN}") String botToken,
            @Value("${DISCORD_CHANNEL_ID}") String channelId) {
        this.restTemplate = restTemplate;
        this.botToken = botToken;
        this.channelId = channelId;
    }

    public void sendMessage(String message) {
        String url = String.format(BASE_URL, channelId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("content", message);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bot " + botToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<DiscordMessageResponse> response =
                    restTemplate.exchange(
                            url, HttpMethod.POST, entity, DiscordMessageResponse.class);
            if (response.getStatusCode() == HttpStatus.OK
                    || response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Discord message sent successfully");
            } else {
                log.warn("Failed to send Discord message: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("Error sending Discord message: {}", e.getMessage());
        }
    }
}
