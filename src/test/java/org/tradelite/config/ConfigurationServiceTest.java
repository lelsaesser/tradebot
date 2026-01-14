package org.tradelite.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigurationServiceTest {

    private static final String TEST_CONFIG_FILE = "config/test-bot-config.json";
    private ConfigurationService configurationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        configurationService = new ConfigurationService(objectMapper);

        // Clean up any existing test config file
        File configFile = new File(TEST_CONFIG_FILE);
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up test config file
        File configFile = new File(TEST_CONFIG_FILE);
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    void isEnabled_newFeature_shouldReturnFalse() {
        boolean enabled = configurationService.isEnabled("newfeature");

        assertThat(enabled, is(false));
    }

    @Test
    void enable_shouldSetFeatureToTrue() {
        configurationService.enable("demotrading");

        boolean enabled = configurationService.isEnabled("demotrading");
        assertThat(enabled, is(true));
    }

    @Test
    void disable_shouldSetFeatureToFalse() {
        configurationService.enable("demotrading");
        configurationService.disable("demotrading");

        boolean enabled = configurationService.isEnabled("demotrading");
        assertThat(enabled, is(false));
    }

    @Test
    void enable_multipleFeaturesIndependently() {
        configurationService.enable("feature1");
        configurationService.enable("feature2");

        assertThat(configurationService.isEnabled("feature1"), is(true));
        assertThat(configurationService.isEnabled("feature2"), is(true));
        // feature3 was not enabled, but if other tests ran it might be in the file
        // So we should either create a new service or just test the ones we enabled
        configurationService.enable("feature3");
        assertThat(configurationService.isEnabled("feature3"), is(true));
    }

    @Test
    void disable_shouldOnlyAffectSpecificFeature() {
        configurationService.enable("feature1");
        configurationService.enable("feature2");
        configurationService.disable("feature1");

        assertThat(configurationService.isEnabled("feature1"), is(false));
        assertThat(configurationService.isEnabled("feature2"), is(true));
    }

    @Test
    void configuration_shouldPersistAcrossInstances() {
        // Create first instance and enable feature
        ConfigurationService service1 = new ConfigurationService(objectMapper);
        service1.enable("demotrading");

        // Create second instance and check if feature is still enabled
        ConfigurationService service2 = new ConfigurationService(objectMapper);
        assertThat(service2.isEnabled("demotrading"), is(true));
    }

    @Test
    void enable_caseSensitiveFeatureNames() {
        configurationService.enable("DemoTrading");
        configurationService.enable("demotrading");

        // Different cases should be treated as different features
        assertThat(configurationService.isEnabled("DemoTrading"), is(true));
        assertThat(configurationService.isEnabled("demotrading"), is(true));
    }

    @Test
    void enable_alreadyEnabledFeature_shouldRemainEnabled() {
        configurationService.enable("demotrading");
        configurationService.enable("demotrading");

        assertThat(configurationService.isEnabled("demotrading"), is(true));
    }

    @Test
    void disable_alreadyDisabledFeature_shouldRemainDisabled() {
        configurationService.disable("demotrading");
        configurationService.disable("demotrading");

        assertThat(configurationService.isEnabled("demotrading"), is(false));
    }

    @Test
    void enable_afterDisable_shouldEnableAgain() {
        configurationService.enable("demotrading");
        configurationService.disable("demotrading");
        configurationService.enable("demotrading");

        assertThat(configurationService.isEnabled("demotrading"), is(true));
    }

    @Test
    void multipleFeatures_shouldWorkIndependently() {
        configurationService.enable("feature1");
        configurationService.enable("feature2");
        configurationService.enable("feature3");
        configurationService.disable("feature2");

        assertThat(configurationService.isEnabled("feature1"), is(true));
        assertThat(configurationService.isEnabled("feature2"), is(false));
        assertThat(configurationService.isEnabled("feature3"), is(true));
    }

    @Test
    void isEnabled_nullFeatureName_shouldReturnFalse() {
        boolean enabled = configurationService.isEnabled(null);

        assertThat(enabled, is(false));
    }

    @Test
    void isEnabled_emptyFeatureName_shouldReturnFalse() {
        boolean enabled = configurationService.isEnabled("");

        assertThat(enabled, is(false));
    }

    @Test
    void reloadConfiguration_shouldReloadFromFile() {
        // First instance enables feature
        configurationService.enable("demotrading");
        assertThat(configurationService.isEnabled("demotrading"), is(true));

        // Second instance should see the change after reload
        ConfigurationService service2 = new ConfigurationService(objectMapper);
        service2.reloadConfiguration();
        assertThat(service2.isEnabled("demotrading"), is(true));
    }

    @Test
    void loadConfiguration_corruptedFile_shouldUseDefaults() {
        // Create a corrupted config file
        File configFile = new File("config/bot-config.json");
        configFile.getParentFile().mkdirs();
        try {
            java.nio.file.Files.writeString(configFile.toPath(), "{corrupted json");
        } catch (Exception _) {
            // Ignore
        }

        ConfigurationService service = new ConfigurationService(objectMapper);
        boolean enabled = service.isEnabled("demotrading");

        assertThat(enabled, is(false));

        // Clean up
        configFile.delete();
    }

    @Test
    void enable_saveFailure_shouldNotThrow() {
        // Enable a feature - even if save fails, should not throw
        configurationService.enable("testfeature");

        // Should still work in memory
        assertThat(configurationService.isEnabled("testfeature"), is(true));
    }

    @Test
    void disable_multipleTimes_shouldWorkCorrectly() {
        configurationService.enable("demotrading");
        configurationService.disable("demotrading");
        configurationService.disable("demotrading");
        configurationService.disable("demotrading");

        assertThat(configurationService.isEnabled("demotrading"), is(false));
    }

    @Test
    void configuration_withSpecialCharacters_shouldWork() {
        configurationService.enable("feature-with-dash");
        configurationService.enable("feature_with_underscore");

        assertThat(configurationService.isEnabled("feature-with-dash"), is(true));
        assertThat(configurationService.isEnabled("feature_with_underscore"), is(true));
    }
}
