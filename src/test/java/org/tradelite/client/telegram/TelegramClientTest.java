package org.tradelite.client.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.tradelite.client.telegram.TelegramClient.BASE_URL;

@ExtendWith(MockitoExtension.class)
class TelegramClientTest {

    @Mock
    private RestTemplate restTemplate;

    private TelegramClient telegramClient;

    @BeforeEach
    void setUp() {
        telegramClient = new TelegramClient(restTemplate, "testToken", "testChatId");
    }

    @Test
    void sendMessage_success() {
        String message = "Test message";
        String expectedUrl = String.format(BASE_URL, "testToken", "testChatId", message);
        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        telegramClient.sendMessage(message);

        verify(restTemplate, times(1)).exchange(eq(expectedUrl), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }
}
