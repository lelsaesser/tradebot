package org.tradelite.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.common.FeatureToggle;

@Slf4j
@Service
public class FeatureToggleService {

    public static final String DEFAULT_FILE_PATH = "config/feature-toggles.json";

    private final ObjectMapper objectMapper;
    private final String filePath;

    private Map<String, Boolean> cachedToggles;

    @Autowired
    public FeatureToggleService(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_FILE_PATH);
    }

    public FeatureToggleService(ObjectMapper objectMapper, String filePath) {
        this.objectMapper = objectMapper;
        this.filePath = filePath;
    }

    @PostConstruct
    protected void init() {
        loadToggles();
    }

    public boolean isEnabled(FeatureToggle feature) {
        return isEnabled(feature.getKey());
    }

    public boolean isEnabled(String featureName) {
        Map<String, Boolean> toggles = getToggles();
        return toggles.getOrDefault(featureName, false);
    }

    public synchronized void setToggle(FeatureToggle feature, boolean enabled) {
        Map<String, Boolean> toggles = readFromFile();
        toggles.put(feature.getKey(), enabled);
        writeToFile(toggles);
        cachedToggles = Collections.unmodifiableMap(toggles);
        log.info("Feature toggle {} set to {}", feature.getKey(), enabled);
    }

    protected Map<String, Boolean> getToggles() {
        if (cachedToggles == null) {
            loadToggles();
        }
        return cachedToggles;
    }

    protected synchronized void loadToggles() {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log.warn("Feature toggles file not found: {}", filePath);
                cachedToggles = Collections.emptyMap();
            } else {
                Map<String, Boolean> loaded =
                        objectMapper.readValue(file, new TypeReference<>() {});
                cachedToggles = Collections.unmodifiableMap(loaded);
            }
            log.debug("Feature toggles loaded: {}", cachedToggles);
        } catch (IOException e) {
            log.error("Failed to load feature toggles from {}", filePath, e);
            if (cachedToggles == null) {
                cachedToggles = Collections.emptyMap();
            }
        }
    }

    private Map<String, Boolean> readFromFile() {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                return objectMapper.readValue(file, new TypeReference<>() {});
            }
        } catch (IOException e) {
            log.error("Failed to read feature toggles from {}", filePath, e);
        }
        return new HashMap<>();
    }

    private void writeToFile(Map<String, Boolean> toggles) {
        try {
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, toggles);
        } catch (IOException e) {
            log.error("Failed to write feature toggles to {}", filePath, e);
            throw new FeatureTogglePersistenceException("Failed to persist feature toggle", e);
        }
    }
}
