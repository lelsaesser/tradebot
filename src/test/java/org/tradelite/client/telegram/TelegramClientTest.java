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
import org.tradelite.client.telegram.dto.TelegramUpdateResponseWrapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.tradelite.client.telegram.TelegramClient.BASE_URL;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        String expectedUrl = String.format(BASE_URL, "testToken", "testChatId", encodedMessage);
        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        telegramClient.sendMessage(message);

        verify(restTemplate, times(1)).exchange(eq(expectedUrl), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void sendMessage_failure() {
        String message = "Test message";
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        String expectedUrl = String.format(BASE_URL, "testToken", "testChatId", encodedMessage);
        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR));

        telegramClient.sendMessage(message);

        verify(restTemplate, times(1)).exchange(eq(expectedUrl), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void sendMessage_exception() {
        String message = "Test message";
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        String expectedUrl = String.format(BASE_URL, "testToken", "testChatId", encodedMessage);
        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RuntimeException("Network error"));

        telegramClient.sendMessage(message);

        verify(restTemplate, times(1)).exchange(eq(expectedUrl), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void getChatUpdates_success() {
        String url = String.format("https://api.telegram.org/bot%s/getUpdates", "testToken");
        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(TelegramUpdateResponseWrapper.class)
        )).thenReturn(new ResponseEntity<>(new TelegramUpdateResponseWrapper(), HttpStatus.OK));

        telegramClient.getChatUpdates();

        verify(restTemplate, times(1)).exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(TelegramUpdateResponseWrapper.class));
    }

    @Test
    void getChatUpdates_exception() {
        String url = String.format("https://api.telegram.org/bot%s/getUpdates", "testToken");
        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(TelegramUpdateResponseWrapper.class)
        )).thenThrow(new RuntimeException("Network error"));

        assertThrows(IllegalStateException.class, () -> telegramClient.getChatUpdates());

        verify(restTemplate, times(1)).exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(TelegramUpdateResponseWrapper.class));
    }
}
