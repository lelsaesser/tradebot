package org.tradelite.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class BeanConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * HTTP client for Yahoo Finance requests. Connect timeout matches ta4j's choice (10s);
     * per-request timeout is set on each {@code HttpRequest} at the call site. See #435.
     */
    @Bean
    public HttpClient yahooHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
}
