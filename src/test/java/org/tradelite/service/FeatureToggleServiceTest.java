package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tradelite.common.FeatureToggle;

class FeatureToggleServiceTest {

    @TempDir Path tempDir;

    private ObjectMapper objectMapper;
    private FeatureToggleService service;
    private Path toggleFilePath;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        toggleFilePath = tempDir.resolve("feature-toggles.json");
        service = new FeatureToggleService(objectMapper, toggleFilePath.toString());
    }

    @Test
    void isEnabled_returnsTrueForEnabledFeature() throws IOException {
        writeToggles(Map.of("myFeature", true));

        boolean result = service.isEnabled("myFeature");

        assertThat(result, is(true));
    }

    @Test
    void isEnabled_returnsFalseForDisabledFeature() throws IOException {
        writeToggles(Map.of("myFeature", false));

        boolean result = service.isEnabled("myFeature");

        assertThat(result, is(false));
    }

    @Test
    void isEnabled_returnsFalseForUnknownFeature() throws IOException {
        writeToggles(Map.of("otherFeature", true));

        boolean result = service.isEnabled("unknownFeature");

        assertThat(result, is(false));
    }

    @Test
    void isEnabled_returnsFalseWhenFileDoesNotExist() {
        boolean result = service.isEnabled("anyFeature");

        assertThat(result, is(false));
    }

    @Test
    void isEnabled_handlesMultipleFeatures() throws IOException {
        writeToggles(
                Map.of(
                        "feature1", true,
                        "feature2", false,
                        "feature3", true));

        assertThat(service.isEnabled("feature1"), is(true));
        assertThat(service.isEnabled("feature2"), is(false));
        assertThat(service.isEnabled("feature3"), is(true));
    }

    @Test
    void isEnabled_handlesFeaturesWithUnderscores() throws IOException {
        writeToggles(Map.of("feature_with_underscore", true));

        assertThat(service.isEnabled("feature_with_underscore"), is(true));
    }

    @Test
    void isEnabled_handlesFeaturesWithDashes() throws IOException {
        writeToggles(Map.of("feature-with-dash", true));

        assertThat(service.isEnabled("feature-with-dash"), is(true));
    }

    @Test
    void getToggles_usesCacheWhenValid() throws IOException {
        writeToggles(Map.of("feature", true));

        service.getToggles();

        writeToggles(Map.of("feature", false));

        Map<String, Boolean> cachedResult = service.getToggles();
        assertThat(cachedResult.get("feature"), is(true));
    }

    @Test
    void getToggles_refreshesCacheAfterInvalidation() throws IOException {
        writeToggles(Map.of("feature", true));
        service.getToggles();

        writeToggles(Map.of("feature", false));
        service.invalidateCache();

        Map<String, Boolean> freshResult = service.getToggles();
        assertThat(freshResult.get("feature"), is(false));
    }

    @Test
    void isCacheValid_returnsFalseWhenCacheIsNull() {
        assertThat(service.isCacheValid(), is(false));
    }

    @Test
    void isCacheValid_returnsTrueAfterRefresh() throws IOException {
        writeToggles(Map.of("feature", true));

        service.refreshCache();

        assertThat(service.isCacheValid(), is(true));
    }

    @Test
    void isCacheValid_returnsFalseAfterInvalidation() throws IOException {
        writeToggles(Map.of("feature", true));

        service.refreshCache();
        service.invalidateCache();

        assertThat(service.isCacheValid(), is(false));
    }

    @Test
    void refreshCache_handlesEmptyFile() throws IOException {
        writeToggles(Map.of());

        Map<String, Boolean> result = service.refreshCache();

        assertThat(result, is(anEmptyMap()));
    }

    @Test
    void refreshCache_returnsEmptyMapWhenFileNotFound() {
        Map<String, Boolean> result = service.refreshCache();

        assertThat(result, is(anEmptyMap()));
    }

    @Test
    void refreshCache_returnsEmptyMapOnReadError() throws IOException {
        writeToggles(Map.of("feature", true));
        service.refreshCache();

        Files.writeString(toggleFilePath, "invalid json {{{");
        service.invalidateCache();
        Map<String, Boolean> result = service.refreshCache();

        assertThat(result, is(anEmptyMap()));
    }

    @Test
    void isEnabled_picksUpChangesAfterCacheInvalidation() throws IOException {
        writeToggles(Map.of("feature", true));
        assertThat(service.isEnabled("feature"), is(true));

        writeToggles(Map.of("feature", false));
        service.invalidateCache();

        assertThat(service.isEnabled("feature"), is(false));
    }

    @Test
    void invalidateCache_allowsFreshRead() throws IOException {
        writeToggles(Map.of("feature", true));
        service.getToggles();

        writeToggles(Map.of("feature", false, "newFeature", true));
        service.invalidateCache();

        assertThat(service.isEnabled("feature"), is(false));
        assertThat(service.isEnabled("newFeature"), is(true));
    }

    @Test
    void defaultConstructor_usesDefaultFilePath() {
        FeatureToggleService defaultService = new FeatureToggleService(objectMapper);

        assertThat(defaultService.isEnabled("nonExistent"), is(false));
    }

    @Test
    void isEnabled_withEnum_returnsCorrectValue() throws IOException {
        writeToggles(Map.of(FeatureToggle.FINNHUB_PRICE_COLLECTION.getKey(), true));

        assertThat(service.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION), is(true));
    }

    @Test
    void isEnabled_withEnum_returnsFalseWhenDisabled() throws IOException {
        writeToggles(Map.of(FeatureToggle.FINNHUB_PRICE_COLLECTION.getKey(), false));

        assertThat(service.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION), is(false));
    }

    private void writeToggles(Map<String, Boolean> toggles) throws IOException {
        File file = toggleFilePath.toFile();
        objectMapper.writeValue(file, toggles);
    }
}
