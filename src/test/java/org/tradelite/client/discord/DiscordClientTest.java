package org.tradelite.client.discord;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.discord.dto.DiscordMessageResponse;

class DiscordClientTest {

    private RestTemplate restTemplate;
    private DiscordClient discordClient;
    private static final String TEST_BOT_TOKEN = "test-bot-token";
    private static final String TEST_CHANNEL_ID = "123456789";

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        discordClient = new DiscordClient(restTemplate, TEST_BOT_TOKEN, TEST_CHANNEL_ID);
    }

    @Test
    void testSendMessage_Success() {
        String message = "Test message";
        DiscordMessageResponse mockResponse = new DiscordMessageResponse();
        mockResponse.setId("999");
        mockResponse.setChannelId(TEST_CHANNEL_ID);
        mockResponse.setContent(message);

        ResponseEntity<DiscordMessageResponse> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(DiscordMessageResponse.class)))
                .thenReturn(responseEntity);

        discordClient.sendMessage(message);

        var entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate)
                .exchange(
                        contains("/channels/" + TEST_CHANNEL_ID + "/messages"),
                        eq(HttpMethod.POST),
                        entityCaptor.capture(),
                        eq(DiscordMessageResponse.class));

        var capturedEntity = entityCaptor.getValue();
        assertEquals(
                "Bot " + TEST_BOT_TOKEN, capturedEntity.getHeaders().getFirst("Authorization"));
        assertEquals(MediaType.APPLICATION_JSON, capturedEntity.getHeaders().getContentType());
    }

    @Test
    void testSendMessage_Created() {
        String message = "Test message";
        DiscordMessageResponse mockResponse = new DiscordMessageResponse();
        mockResponse.setId("999");

        ResponseEntity<DiscordMessageResponse> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.CREATED);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(DiscordMessageResponse.class)))
                .thenReturn(responseEntity);

        assertDoesNotThrow(() -> discordClient.sendMessage(message));
    }

    @Test
    void testSendMessage_Failure() {
        String message = "Test message";

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(DiscordMessageResponse.class)))
                .thenThrow(new RuntimeException("API Error"));

        assertDoesNotThrow(() -> discordClient.sendMessage(message));
    }

    @Test
    void testBaseUrl() {
        assertEquals("https://discord.com/api/v10/channels/%s/messages", DiscordClient.BASE_URL);
    }
}
