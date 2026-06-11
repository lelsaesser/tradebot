package org.tradelite.client.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.tradelite.client.telegram.TelegramClient.BASE_URL;
import static org.tradelite.client.telegram.TelegramClient.DELETE_URL;
import static org.tradelite.client.telegram.TelegramMessageSanitizer.LIMIT;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.telegram.dto.TelegramMessage;
import org.tradelite.client.telegram.dto.TelegramSendMessageResponse;
import org.tradelite.client.telegram.dto.TelegramUpdateResponseWrapper;
import org.tradelite.config.TradebotTelegramProperties;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
// Tests share the TelegramClient logger via ListAppender; serialize within this class so
// concurrent tests do not leak log events into each other's appenders (would otherwise cause
// ConcurrentModificationException on the shared ArrayList).
@Execution(ExecutionMode.SAME_THREAD)
class TelegramClientTest {

    @Mock private RestTemplate restTemplate;

    private TelegramClient telegramClient;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger telegramClientLogger;

    @BeforeEach
    void setUp() {
        TradebotTelegramProperties properties = new TradebotTelegramProperties();
        properties.setBotToken("testToken");
        properties.setGroupChatId("testChatId");
        telegramClient = new TelegramClient(restTemplate, properties);

        telegramClientLogger = (Logger) LoggerFactory.getLogger(TelegramClient.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        telegramClientLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        telegramClientLogger.detachAppender(logAppender);
    }

    @Test
    void sendMessageAndReturnId_success() {
        TelegramMessage message = new TelegramMessage();
        message.setMessageId(42L);
        TelegramSendMessageResponse body = new TelegramSendMessageResponse();
        body.setOk(true);
        body.setResult(message);

        String expectedUrl = String.format(BASE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(TelegramSendMessageResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        OptionalLong result = telegramClient.sendMessageAndReturnId("Test message");

        assertTrue(result.isPresent());
        assertEquals(42L, result.getAsLong());
    }

    @Test
    void sendMessageAndReturnId_apiNotOk_returnsEmpty() {
        TelegramSendMessageResponse body = new TelegramSendMessageResponse();
        body.setOk(false);

        String expectedUrl = String.format(BASE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(TelegramSendMessageResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        OptionalLong result = telegramClient.sendMessageAndReturnId("Test message");

        assertTrue(result.isEmpty());
    }

    @Test
    void sendMessageAndReturnId_exception_returnsEmpty() {
        String expectedUrl = String.format(BASE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(TelegramSendMessageResponse.class)))
                .thenThrow(new RuntimeException("Network error"));

        OptionalLong result = telegramClient.sendMessageAndReturnId("Test message");

        assertTrue(result.isEmpty());
    }

    @Test
    void sendMessage_delegatesToSendMessageAndReturnId() {
        TelegramMessage message = new TelegramMessage();
        message.setMessageId(99L);
        TelegramSendMessageResponse body = new TelegramSendMessageResponse();
        body.setOk(true);
        body.setResult(message);

        String expectedUrl = String.format(BASE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(TelegramSendMessageResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        telegramClient.sendMessage("Test message");

        verify(restTemplate, times(1))
                .exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(TelegramSendMessageResponse.class));
    }

    @Test
    void deleteMessage_success() {
        String expectedUrl = String.format(DELETE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        telegramClient.deleteMessage(123L);

        verify(restTemplate, times(1))
                .exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(String.class));
    }

    @Test
    void deleteMessage_failure_doesNotThrow() {
        String expectedUrl = String.format(DELETE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(String.class)))
                .thenReturn(new ResponseEntity<>("error", HttpStatus.BAD_REQUEST));

        assertDoesNotThrow(() -> telegramClient.deleteMessage(123L));
    }

    @Test
    void deleteMessage_exception_doesNotThrow() {
        String expectedUrl = String.format(DELETE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        assertDoesNotThrow(() -> telegramClient.deleteMessage(123L));
    }

    @Test
    void sendMessageAndReturnId_underLimit_sendsWithMarkdownParseMode() {
        TelegramMessage message = new TelegramMessage();
        message.setMessageId(1L);
        TelegramSendMessageResponse body = new TelegramSendMessageResponse();
        body.setOk(true);
        body.setResult(message);

        String expectedUrl = String.format(BASE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(TelegramSendMessageResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        telegramClient.sendMessageAndReturnId("*Hello* _world_");

        ArgumentCaptor<HttpEntity<java.util.Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate)
                .exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        captor.capture(),
                        eq(TelegramSendMessageResponse.class));
        java.util.Map<String, Object> payload = captor.getValue().getBody();
        assertNotNull(payload);
        assertEquals("Markdown", payload.get("parse_mode"));
        assertEquals("*Hello* _world_", payload.get("text"));
    }

    @Test
    void sendMessageAndReturnId_strippedOnly_sendsWithoutParseMode() {
        // Message that is over the limit but fits after stripping markers.
        String plain = "x".repeat(LIMIT - 100);
        String markers = "*".repeat(200);
        String oversized = plain + markers;
        assertTrue(oversized.length() > LIMIT);

        TelegramMessage message = new TelegramMessage();
        message.setMessageId(2L);
        TelegramSendMessageResponse body = new TelegramSendMessageResponse();
        body.setOk(true);
        body.setResult(message);

        String expectedUrl = String.format(BASE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(TelegramSendMessageResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        telegramClient.sendMessageAndReturnId(oversized);

        ArgumentCaptor<HttpEntity<java.util.Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate)
                .exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        captor.capture(),
                        eq(TelegramSendMessageResponse.class));
        java.util.Map<String, Object> payload = captor.getValue().getBody();
        assertNotNull(payload);
        assertFalse(payload.containsKey("parse_mode"));
        String text = (String) payload.get("text");
        assertNotNull(text);
        assertTrue(text.length() <= LIMIT);
        assertFalse(text.contains("*"));
        assertFalse(text.contains("_"));
        assertFalse(text.contains("`"));
    }

    @Test
    void sendMessageAndReturnId_truncated_sendsWithoutParseMode() {
        // Plain text well over the limit with newlines for cut boundary.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("row-").append(i).append("\n");
        }
        String oversized = sb.toString();
        assertTrue(oversized.length() > LIMIT);

        TelegramMessage message = new TelegramMessage();
        message.setMessageId(3L);
        TelegramSendMessageResponse body = new TelegramSendMessageResponse();
        body.setOk(true);
        body.setResult(message);

        String expectedUrl = String.format(BASE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(TelegramSendMessageResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        telegramClient.sendMessageAndReturnId(oversized);

        ArgumentCaptor<HttpEntity<java.util.Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate)
                .exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        captor.capture(),
                        eq(TelegramSendMessageResponse.class));
        java.util.Map<String, Object> payload = captor.getValue().getBody();
        assertNotNull(payload);
        assertFalse(payload.containsKey("parse_mode"));
        String text = (String) payload.get("text");
        assertNotNull(text);
        assertTrue(text.length() <= LIMIT);
    }

    @Test
    void sendMessageAndReturnId_exactlyAtCharLimit_doesNotThrow() {
        String maxMessage = "x".repeat(LIMIT);

        TelegramMessage message = new TelegramMessage();
        message.setMessageId(1L);
        TelegramSendMessageResponse body = new TelegramSendMessageResponse();
        body.setOk(true);
        body.setResult(message);

        String expectedUrl = String.format(BASE_URL, "testToken");
        when(restTemplate.exchange(
                        eq(expectedUrl),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(TelegramSendMessageResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        OptionalLong result = telegramClient.sendMessageAndReturnId(maxMessage);

        assertTrue(result.isPresent());
    }

    @Test
    void getChatUpdates_success() {
        String url = String.format("https://api.telegram.org/bot%s/getUpdates", "testToken");
        when(restTemplate.exchange(
                        eq(url),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(TelegramUpdateResponseWrapper.class)))
                .thenReturn(
                        new ResponseEntity<>(new TelegramUpdateResponseWrapper(), HttpStatus.OK));

        telegramClient.getChatUpdates();

        verify(restTemplate, times(1))
                .exchange(
                        eq(url),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(TelegramUpdateResponseWrapper.class));
    }

    @Test
    void getChatUpdates_exception() {
        String url = String.format("https://api.telegram.org/bot%s/getUpdates", "testToken");
        when(restTemplate.exchange(
                        eq(url),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(TelegramUpdateResponseWrapper.class)))
                .thenThrow(new RuntimeException("Network error"));

        assertThrows(IllegalStateException.class, () -> telegramClient.getChatUpdates());
    }

    // --- Token redaction (#484) ---
    //
    // Token shape matches the SecretRedactingTurboFilter pattern (8–10 digits, ':', 35 chars of
    // [A-Za-z0-9_-]) so a missed redaction would also be caught by the global filter — which is
    // exactly the original-#484 symptom we want to prevent.
    private static final String FAKE_TOKEN = "1234567890:AAEabcdefghijklmnopqrstuvwxyzABCDEFGHI";

    private static String tokenBearingRestTemplateMessage(String urlPath) {
        // Same shape as Spring's RestClientResponseException.getMessage(): the URL leaks via the
        // human-readable message string.
        return "400 Bad Request on POST request for \"https://api.telegram.org/bot"
                + FAKE_TOKEN
                + urlPath
                + "\": \"...\"";
    }

    @Test
    void sendMessageAndReturnId_exception_logRedactsToken() {
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(TelegramSendMessageResponse.class)))
                .thenThrow(new RuntimeException(tokenBearingRestTemplateMessage("/sendMessage")));

        OptionalLong result = telegramClient.sendMessageAndReturnId("anything");

        assertTrue(result.isEmpty());
        assertThat(logAppender.list)
                .anySatisfy(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                            assertThat(event.getFormattedMessage())
                                    .contains("Error sending message")
                                    .contains("/bot<redacted>/")
                                    .doesNotContain(FAKE_TOKEN);
                        });
    }

    @Test
    void deleteMessage_exception_logRedactsToken() {
        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException(tokenBearingRestTemplateMessage("/deleteMessage")));

        telegramClient.deleteMessage(7L);

        assertThat(logAppender.list)
                .anySatisfy(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.WARN);
                            assertThat(event.getFormattedMessage())
                                    .contains("Error deleting message")
                                    .contains("/bot<redacted>/")
                                    .doesNotContain(FAKE_TOKEN);
                        });
    }

    @Test
    void getChatUpdates_exception_logAndRethrowBothRedactToken() {
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(TelegramUpdateResponseWrapper.class)))
                .thenThrow(new RuntimeException(tokenBearingRestTemplateMessage("/getUpdates")));

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> telegramClient.getChatUpdates());

        // Rethrown exception's message is what RootErrorHandler ultimately logs; redaction here is
        // what closes the second log line in issue #484.
        assertThat(thrown.getMessage()).contains("/bot<redacted>/").doesNotContain(FAKE_TOKEN);

        assertThat(logAppender.list)
                .anySatisfy(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                            assertThat(event.getFormattedMessage())
                                    .contains("Error fetching chat updates")
                                    .contains("/bot<redacted>/")
                                    .doesNotContain(FAKE_TOKEN);
                        });
    }

    @Test
    void redact_nullInput_returnsNull() {
        // Null-safety contract: callers pass e.getMessage() which can be null for some exceptions.
        assertNull(TelegramClient.redact(null));
    }

    @Test
    void redact_noTokenPresent_returnsInputUnchanged() {
        String plain = "Connection timed out after 5s";
        assertEquals(plain, TelegramClient.redact(plain));
    }
}
