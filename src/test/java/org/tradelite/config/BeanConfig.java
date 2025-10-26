package org.tradelite.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class BeanConfigTest {

    private final BeanConfig beanConfig = new BeanConfig();

    @Test
    void restTemplateBean_shouldNotBeNull() {
        RestTemplate restTemplate = beanConfig.restTemplate();
        assertNotNull(restTemplate);
    }

    @Test
    void objectMapperBean_shouldNotBeNull() {
        ObjectMapper objectMapper = beanConfig.objectMapper();
        assertNotNull(objectMapper);
    }
}
