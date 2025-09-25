package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.repository.DailyPriceRepository;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataMigrationServiceTest {

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private ObjectMapper objectMapper;

    private DataMigrationService dataMigrationService;

    @BeforeEach
    void setUp() {
        reset(dailyPriceRepository, objectMapper);
        
        dataMigrationService = spy(new DataMigrationService(dailyPriceRepository, objectMapper));
    }

    @Test
    void testMigrateJsonDataToDatabase_fileNotExists() {
        doReturn(false).when(dataMigrationService).fileExists(any(File.class));
        
        dataMigrationService.migrateJsonDataToDatabase();

        verifyNoInteractions(objectMapper);
        verifyNoInteractions(dailyPriceRepository);
    }

    @Test
    void testIsMigrationNeeded_jsonExistsDbEmpty() {
        when(dailyPriceRepository.count()).thenReturn(0L);

        boolean result = dataMigrationService.isMigrationNeeded();

        assertTrue(result); // Will be true because JSON file exists and DB is empty
        verify(dailyPriceRepository).count();
    }

    @Test
    void testIsMigrationNeeded_jsonExistsDbNotEmpty() {
        when(dailyPriceRepository.count()).thenReturn(10L);

        boolean result = dataMigrationService.isMigrationNeeded();

        assertFalse(result);
        verify(dailyPriceRepository).count();
    }

    @Test
    void testGetMigrationStatus() {
        when(dailyPriceRepository.count()).thenReturn(5L);

        String status = dataMigrationService.getMigrationStatus();

        assertNotNull(status);
        assertTrue(status.contains("JSON file exists: true")); // File actually exists
        assertTrue(status.contains("Database records: 5"));
        verify(dailyPriceRepository).count();
    }

    @Test
    void testGetMigrationStatus_emptyDatabase() {
        when(dailyPriceRepository.count()).thenReturn(0L);

        String status = dataMigrationService.getMigrationStatus();

        assertNotNull(status);
        assertTrue(status.contains("Database records: 0"));
        verify(dailyPriceRepository).count();
    }

    @Test
    void testGetMigrationStatus_largeDatabaseCount() {
        when(dailyPriceRepository.count()).thenReturn(1000L);

        String status = dataMigrationService.getMigrationStatus();

        assertNotNull(status);
        assertTrue(status.contains("Database records: 1000"));
        verify(dailyPriceRepository).count();
    }
}
