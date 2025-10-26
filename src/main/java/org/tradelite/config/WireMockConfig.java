package org.tradelite.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

/**
 * WireMock configuration for local development in "stub" profile.
 *
 * When the "stub" Spring profile is active (SPRING_PROFILES_ACTIVE=stub), this configuration
 * starts an embedded WireMock HTTP server on port 8089. External API clients are expected
 * to be pointed at http://localhost:8089 via profile-specific properties (see application-stub.yaml).
 *
 * Mappings can be provided as JSON files under:
 *   src/main/resources/wiremock/mappings   (request/response specs)
 *   src/main/resources/wiremock/__files    (large bodies or templated files)
 *
 * If no explicit mapping files exist for a given endpoint, a small set of programmatic
 * fallback stubs are registered to prevent unexpected 404s during development.
 *
 * NOTE: This bean is not loaded in non-stub profiles, ensuring production calls
 * go to real external services.
 */
@Slf4j
@Configuration
@Profile("stub")
public class WireMockConfig {

    /**
     * Creates and starts the WireMockServer. Uses a fixed port (8089) so that
     * property overrides remain stable and Docker/Compose or scripts can rely on it.
     *
     * Loaded mappings:
     *   - Classpath directory "wiremock" (so /wiremock/mappings/*.json will be auto-loaded)
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public WireMockServer wireMockServer() {
        WireMockConfiguration options = WireMockConfiguration.options()
                .port(8089)
                .usingFilesUnderClasspath("wiremock")
                // Disable request journal if you prefer lower memory; keep enabled for debugging.
                //.disableRequestJournal()
                ;

        WireMockServer server = new WireMockServer(options);
        // Server not started yet; initMethod will start it. We register programmatic stubs
        // via a second bean once started (see below).
        return server;
    }

    /**
     * Registers fallback programmatic stubs AFTER the server starts.
     * This avoids cluttering the mappings directory with simple defaults and provides
     * immediate feedback while you incrementally build mapping JSON files.
     *
     * You can remove or modify these as you add concrete mapping files.
     */
    @Bean
    public FallbackWireMockInitializer fallbackWireMockInitializer(WireMockServer server) {
        return new FallbackWireMockInitializer(server);
    }

    /**
     * Helper component responsible for adding fallback stubs.
     */
    public static class FallbackWireMockInitializer {

        private final WireMockServer server;

        public FallbackWireMockInitializer(WireMockServer server) {
            this.server = server;
            registerFallbacks();
        }

        private void registerFallbacks() {
            log.info("WireMock (stub profile) starting on port {}. Registering fallback stubs if needed.", server.getOptions().portNumber());

            registerIfAbsent("/api/v1/quote", () -> {
                server.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/quote"))
                        .willReturn(WireMock.okJson("""
                                {
                                  "c": 123.45,
                                  "o": 120.00,
                                  "h": 125.00,
                                  "l": 119.00,
                                  "d": 3.45,
                                  "dp": 2.87,
                                  "pc": 120.00
                                }
                                """))
                        .atPriority(10));
            });

            registerIfAbsent("/api/v1/stock/insider-transactions", () -> {
                server.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/stock/insider-transactions"))
                        .willReturn(WireMock.okJson("""
                                {
                                  "data": [
                                    {
                                      "name": "Jane Doe",
                                      "share": 1000,
                                      "change": 1000,
                                      "filingDate": "2024-10-01",
                                      "transactionDate": "2024-09-30",
                                      "transactionCode": "P",
                                      "transactionPrice": 121.10
                                    }
                                  ]
                                }
                                """))
                        .atPriority(10));
            });

            registerIfAbsent("/api/v3/simple/price", () -> {
                server.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v3/simple/price"))
                        .willReturn(WireMock.okJson("""
                                {
                                  "bitcoin": {
                                    "usd": 64000.12,
                                    "usd_24h_change": 1.25
                                  }
                                }
                                """))
                        .atPriority(10));
            });

            registerIfAbsentPattern("/bot.*?/sendMessage", () -> {
                server.stubFor(WireMock.post(WireMock.urlPathMatching("/bot.*?/sendMessage"))
                        .willReturn(WireMock.okJson("""
                                {
                                  "ok": true,
                                  "result": {
                                    "message_id": 1,
                                    "chat": {"id": 999999, "type": "group", "title": "Stub Chat"},
                                    "date": 1729900000,
                                    "text": "stubbed"
                                  }
                                }
                                """))
                        .atPriority(10));
            });

            registerIfAbsentPattern("/bot.*?/getUpdates", () -> {
                server.stubFor(WireMock.get(WireMock.urlPathMatching("/bot.*?/getUpdates"))
                        .willReturn(WireMock.okJson("""
                                {
                                  "ok": true,
                                  "result": []
                                }
                                """))
                        .atPriority(10));
            });

            // Example of simulating an intermittent fault for a specific path (disabled by default):
            // registerIfAbsent("/api/v1/unstable", () -> {
            //     server.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/unstable"))
            //             .willReturn(WireMock.aResponse()
            //                     .withStatus(500)
            //                     .withBody("{\"error\":\"temporary failure\"}")
            //                     .withFault(Fault.EMPTY_RESPONSE))
            //             .atPriority(5));
            // });

            log.info("Fallback WireMock stubs registered. Add JSON mappings under 'wiremock/mappings' to override.");
        }

        /**
         * Registers a stub only if no existing mapping's URL path equals the requested path.
         */
        private void registerIfAbsent(String exactPath, Runnable registrar) {
            boolean exists = server.getStubMappings().getAll().stream()
                    .anyMatch(m -> Optional.ofNullable(m.getRequest())
                            .map(r -> r.getUrlMatcher())
                            .filter(UrlPattern.class::isInstance)
                            .map(UrlPattern.class::cast)
                            .map(UrlPattern::getExpected)
                            .filter(exactPath::equals)
                            .isPresent());

            if (!exists) {
                registrar.run();
                log.debug("Registered fallback stub for path: {}", exactPath);
            } else {
                log.debug("Mapping already exists for path {}, skipping fallback.", exactPath);
            }
        }

        /**
         * Registers using a regex pattern if no mapping with that pattern exists.
         */
        private void registerIfAbsentPattern(String regexPath, Runnable registrar) {
            boolean exists = server.getStubMappings().getAll().stream()
                    .anyMatch(m -> Optional.ofNullable(m.getRequest())
                            .map(r -> r.getUrlMatcher())
                            .filter(UrlPattern.class::isInstance)
                            .map(UrlPattern.class::cast)
                            .map(UrlPattern::getRegex)
                            .filter(regexPath::equals)
                            .isPresent());

            if (!exists) {
                registrar.run();
                log.debug("Registered fallback stub for pattern: {}", regexPath);
            } else {
                log.debug("Mapping already exists for pattern {}, skipping fallback.", regexPath);
            }
        }
    }
}
