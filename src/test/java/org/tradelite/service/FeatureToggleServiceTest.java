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
        service.init();

        boolean result = service.isEnabled("myFeature");

        assertThat(result, is(true));
    }

    @Test
    void isEnabled_returnsFalseForDisabledFeature() throws IOException {
        writeToggles(Map.of("myFeature", false));
        service.init();

        boolean result = service.isEnabled("myFeature");

        assertThat(result, is(false));
    }

    @Test
    void isEnabled_returnsFalseForUnknownFeature() throws IOException {
        writeToggles(Map.of("otherFeature", true));
        service.init();

        boolean result = service.isEnabled("unknownFeature");

        assertThat(result, is(false));
    }

    @Test
    void isEnabled_returnsFalseWhenFileDoesNotExist() {
        service.init();

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
        service.init();

        assertThat(service.isEnabled("feature1"), is(true));
        assertThat(service.isEnabled("feature2"), is(false));
        assertThat(service.isEnabled("feature3"), is(true));
    }

    @Test
    void init_loadsTogglesFromFile() throws IOException {
        writeToggles(Map.of("feature", true));

        service.init();

        assertThat(service.isEnabled("feature"), is(true));
    }

    @Test
    void init_handlesEmptyFile() throws IOException {
        writeToggles(Map.of());

        service.init();

        assertThat(service.getToggles(), is(anEmptyMap()));
    }

    @Test
    void init_handlesMissingFile() {
        service.init();

        assertThat(service.getToggles(), is(anEmptyMap()));
    }

    @Test
    void init_handlesInvalidJson() throws IOException {
        Files.writeString(toggleFilePath, "invalid json {{{");

        service.init();

        assertThat(service.getToggles(), is(anEmptyMap()));
    }

    @Test
    void setToggle_enablesFeature() throws IOException {
        writeToggles(Map.of(FeatureToggle.EMA_REPORT.getKey(), false));
        service.init();

        service.setToggle(FeatureToggle.EMA_REPORT, true);

        assertThat(service.isEnabled(FeatureToggle.EMA_REPORT), is(true));
    }

    @Test
    void setToggle_disablesFeature() throws IOException {
        writeToggles(Map.of(FeatureToggle.EMA_REPORT.getKey(), true));
        service.init();

        service.setToggle(FeatureToggle.EMA_REPORT, false);

        assertThat(service.isEnabled(FeatureToggle.EMA_REPORT), is(false));
    }

    @Test
    void setToggle_createsFileIfMissing() {
        service.init();

        service.setToggle(FeatureToggle.EMA_REPORT, true);

        assertThat(toggleFilePath.toFile().exists(), is(true));
        assertThat(service.isEnabled(FeatureToggle.EMA_REPORT), is(true));
    }

    @Test
    void setToggle_insertsNewToggleIntoExistingFile() throws IOException {
        writeToggles(Map.of(FeatureToggle.EMA_REPORT.getKey(), true));
        service.init();

        service.setToggle(FeatureToggle.VFI_REPORT, true);

        assertThat(service.isEnabled(FeatureToggle.EMA_REPORT), is(true));
        assertThat(service.isEnabled(FeatureToggle.VFI_REPORT), is(true));
    }

    @Test
    void setToggle_persistsToFile() throws IOException {
        writeToggles(Map.of(FeatureToggle.EMA_REPORT.getKey(), false));
        service.init();

        service.setToggle(FeatureToggle.EMA_REPORT, true);

        // Create a new service instance pointing to same file to verify persistence
        FeatureToggleService freshService =
                new FeatureToggleService(objectMapper, toggleFilePath.toString());
        freshService.init();
        assertThat(freshService.isEnabled(FeatureToggle.EMA_REPORT), is(true));
    }

    @Test
    void setToggle_updatesInMemoryCacheImmediately() throws IOException {
        writeToggles(Map.of(FeatureToggle.EMA_REPORT.getKey(), false));
        service.init();

        service.setToggle(FeatureToggle.EMA_REPORT, true);

        // Corrupt the file to prove the result comes from in-memory cache, not a file re-read
        Files.writeString(toggleFilePath, "invalid json {{{");
        assertThat(service.isEnabled(FeatureToggle.EMA_REPORT), is(true));
    }

    @Test
    void setToggle_preservesOtherTogglesInFile() throws IOException {
        writeToggles(
                Map.of(
                        FeatureToggle.EMA_REPORT.getKey(), true,
                        FeatureToggle.VFI_REPORT.getKey(), true));
        service.init();

        service.setToggle(FeatureToggle.EMA_REPORT, false);

        assertThat(service.isEnabled(FeatureToggle.EMA_REPORT), is(false));
        assertThat(service.isEnabled(FeatureToggle.VFI_REPORT), is(true));
    }

    @Test
    void isEnabled_withEnum_returnsCorrectValue() throws IOException {
        writeToggles(Map.of(FeatureToggle.FINNHUB_PRICE_COLLECTION.getKey(), true));
        service.init();

        assertThat(service.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION), is(true));
    }

    @Test
    void isEnabled_withEnum_returnsFalseWhenDisabled() throws IOException {
        writeToggles(Map.of(FeatureToggle.FINNHUB_PRICE_COLLECTION.getKey(), false));
        service.init();

        assertThat(service.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION), is(false));
    }

    @Test
    void defaultConstructor_usesDefaultFilePath() {
        FeatureToggleService defaultService = new FeatureToggleService(objectMapper);

        assertThat(defaultService.isEnabled("nonExistent"), is(false));
    }

    private void writeToggles(Map<String, Boolean> toggles) throws IOException {
        File file = toggleFilePath.toFile();
        objectMapper.writeValue(file, toggles);
    }
}
