package org.tradelite.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private static final String CONFIG_FILE = "config/bot-config.json";

    private final ObjectMapper objectMapper;
    private Map<String, Boolean> configuration;

    public boolean isEnabled(String feature) {
        loadConfiguration();
        return configuration.getOrDefault(feature, false);
    }

    public void enable(String feature) {
        loadConfiguration();
        configuration.put(feature, true);
        saveConfiguration();
        log.info("Enabled feature: {}", feature);
    }

    public void disable(String feature) {
        loadConfiguration();
        configuration.put(feature, false);
        saveConfiguration();
        log.info("Disabled feature: {}", feature);
    }

    private void loadConfiguration() {
        if (configuration != null) {
            return; // Already loaded
        }

        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            log.info("Configuration file not found, creating default configuration");
            configuration = new HashMap<>();
            saveConfiguration();
            return;
        }

        try {
            configuration =
                    objectMapper.readValue(
                            file,
                            objectMapper
                                    .getTypeFactory()
                                    .constructMapType(HashMap.class, String.class, Boolean.class));
            log.debug("Loaded configuration with {} features", configuration.size());
        } catch (IOException e) {
            log.error("Failed to load configuration, using defaults", e);
            configuration = new HashMap<>();
        }
    }

    private void saveConfiguration() {
        File file = new File(CONFIG_FILE);
        try {
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, configuration);
            log.debug("Configuration saved successfully");
        } catch (IOException e) {
            log.error("Failed to save configuration", e);
        }
    }

    public void reloadConfiguration() {
        configuration = null;
        loadConfiguration();
    }
}
