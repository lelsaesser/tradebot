package org.tradelite.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FeatureToggleService {

    public static final String DEFAULT_FILE_PATH = "config/feature-toggles.json";
    public static final long CACHE_TTL_SECONDS = 180L;

    private final ObjectMapper objectMapper;
    private final String filePath;

    private Map<String, Boolean> cachedToggles;
    private Instant cacheTimestamp;

    @Autowired
    public FeatureToggleService(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_FILE_PATH);
    }

    public FeatureToggleService(ObjectMapper objectMapper, String filePath) {
        this.objectMapper = objectMapper;
        this.filePath = filePath;
    }

    public boolean isEnabled(String featureName) {
        Map<String, Boolean> toggles = getToggles();
        return toggles.getOrDefault(featureName, false);
    }

    protected Map<String, Boolean> getToggles() {
        if (isCacheValid()) {
            return cachedToggles;
        }
        return refreshCache();
    }

    protected boolean isCacheValid() {
        if (cachedToggles == null || cacheTimestamp == null) {
            return false;
        }
        long elapsedSeconds = Duration.between(cacheTimestamp, Instant.now()).getSeconds();
        return elapsedSeconds < CACHE_TTL_SECONDS;
    }

    protected synchronized Map<String, Boolean> refreshCache() {
        if (isCacheValid()) {
            return cachedToggles;
        }

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log.warn("Feature toggles file not found: {}", filePath);
                cachedToggles = Collections.emptyMap();
            } else {
                cachedToggles = objectMapper.readValue(file, new TypeReference<>() {});
            }
            cacheTimestamp = Instant.now();
            log.debug("Feature toggles cache refreshed: {}", cachedToggles);
        } catch (IOException e) {
            log.error("Failed to load feature toggles from {}", filePath, e);
            if (cachedToggles == null) {
                cachedToggles = Collections.emptyMap();
            }
        }
        return cachedToggles;
    }

    protected void invalidateCache() {
        cachedToggles = null;
        cacheTimestamp = null;
    }
}
