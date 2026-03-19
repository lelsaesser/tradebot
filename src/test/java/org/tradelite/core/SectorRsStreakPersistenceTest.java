package org.tradelite.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SectorRsStreakPersistenceTest {

    @TempDir Path tempDir;

    private ObjectMapper objectMapper;
    private SectorRsStreakPersistence persistence;
    private String testFilePath;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        testFilePath = tempDir.resolve("test-streaks.json").toString();
        persistence = new SectorRsStreakPersistence(objectMapper, testFilePath);
    }

    @Test
    void updateStreak_newSector_createsStreakWithDayOne() throws IOException {
        LocalDate today = LocalDate.of(2026, 3, 19);

        SectorRsStreak streak = persistence.updateStreak("XLK", true, today);

        assertEquals("XLK", streak.symbol());
        assertEquals(1, streak.streakDays());
        assertTrue(streak.isOutperforming());
        assertEquals(today, streak.lastUpdated());
    }

    @Test
    void updateStreak_sameDirectionNextDay_incrementsStreak() throws IOException {
        LocalDate day1 = LocalDate.of(2026, 3, 18);
        LocalDate day2 = LocalDate.of(2026, 3, 19);

        persistence.updateStreak("XLK", true, day1);
        SectorRsStreak streak = persistence.updateStreak("XLK", true, day2);

        assertEquals(2, streak.streakDays());
        assertTrue(streak.isOutperforming());
    }

    @Test
    void updateStreak_directionChange_resetsStreak() throws IOException {
        LocalDate day1 = LocalDate.of(2026, 3, 18);
        LocalDate day2 = LocalDate.of(2026, 3, 19);

        persistence.updateStreak("XLK", true, day1);
        SectorRsStreak streak = persistence.updateStreak("XLK", false, day2);

        assertEquals(1, streak.streakDays());
        assertFalse(streak.isOutperforming());
    }

    @Test
    void updateStreak_sameDay_returnsExistingStreak() throws IOException {
        LocalDate today = LocalDate.of(2026, 3, 19);

        SectorRsStreak first = persistence.updateStreak("XLK", true, today);
        SectorRsStreak second = persistence.updateStreak("XLK", true, today);

        assertEquals(first.streakDays(), second.streakDays());
        assertEquals(1, second.streakDays());
    }

    @Test
    void updateStreak_multipleSectors_tracksIndependently() throws IOException {
        LocalDate day1 = LocalDate.of(2026, 3, 18);
        LocalDate day2 = LocalDate.of(2026, 3, 19);

        persistence.updateStreak("XLK", true, day1);
        persistence.updateStreak("XLU", false, day1);

        persistence.updateStreak("XLK", true, day2);
        persistence.updateStreak("XLU", false, day2);

        SectorRsStreak xlk = persistence.getStreak("XLK").orElseThrow();
        SectorRsStreak xlu = persistence.getStreak("XLU").orElseThrow();

        assertEquals(2, xlk.streakDays());
        assertTrue(xlk.isOutperforming());

        assertEquals(2, xlu.streakDays());
        assertFalse(xlu.isOutperforming());
    }

    @Test
    void getStreak_unknownSymbol_returnsEmpty() {
        Optional<SectorRsStreak> streak = persistence.getStreak("UNKNOWN");

        assertTrue(streak.isEmpty());
    }

    @Test
    void getStreak_existingSymbol_returnsStreak() throws IOException {
        LocalDate today = LocalDate.of(2026, 3, 19);
        persistence.updateStreak("XLK", true, today);

        Optional<SectorRsStreak> streak = persistence.getStreak("XLK");

        assertTrue(streak.isPresent());
        assertEquals("XLK", streak.get().symbol());
    }

    @Test
    void getAllStreaks_noData_returnsEmptyMap() {
        Map<String, SectorRsStreak> streaks = persistence.getAllStreaks();

        assertTrue(streaks.isEmpty());
    }

    @Test
    void getAllStreaks_withData_returnsAllStreaks() throws IOException {
        LocalDate today = LocalDate.of(2026, 3, 19);
        persistence.updateStreak("XLK", true, today);
        persistence.updateStreak("XLU", false, today);
        persistence.updateStreak("XLF", true, today);

        Map<String, SectorRsStreak> streaks = persistence.getAllStreaks();

        assertEquals(3, streaks.size());
        assertTrue(streaks.containsKey("XLK"));
        assertTrue(streaks.containsKey("XLU"));
        assertTrue(streaks.containsKey("XLF"));
    }

    @Test
    void persistence_survivesBetweenInstances() throws IOException {
        LocalDate day1 = LocalDate.of(2026, 3, 18);
        LocalDate day2 = LocalDate.of(2026, 3, 19);

        // First instance
        persistence.updateStreak("XLK", true, day1);

        // Create new instance pointing to same file
        SectorRsStreakPersistence newPersistence =
                new SectorRsStreakPersistence(objectMapper, testFilePath);
        SectorRsStreak streak = newPersistence.updateStreak("XLK", true, day2);

        assertEquals(2, streak.streakDays());
    }

    @Test
    void loadStreaks_fileDoesNotExist_returnsEmptyMap() {
        String nonExistentFile = tempDir.resolve("nonexistent.json").toString();
        SectorRsStreakPersistence emptyPersistence =
                new SectorRsStreakPersistence(objectMapper, nonExistentFile);

        Map<String, SectorRsStreak> streaks = emptyPersistence.loadStreaks();

        assertTrue(streaks.isEmpty());
    }

    @Test
    void updateStreak_createsFile() throws IOException {
        LocalDate today = LocalDate.of(2026, 3, 19);

        persistence.updateStreak("XLK", true, today);

        File file = new File(testFilePath);
        assertTrue(file.exists());
    }

    @Test
    void updateStreak_longStreak_tracksCorrectly() throws IOException {
        LocalDate startDate = LocalDate.of(2026, 3, 1);

        // Simulate 15 consecutive days of outperformance
        for (int i = 0; i < 15; i++) {
            persistence.updateStreak("XLK", true, startDate.plusDays(i));
        }

        SectorRsStreak streak = persistence.getStreak("XLK").orElseThrow();
        assertEquals(15, streak.streakDays());
        assertTrue(streak.isOutperforming());
    }

    @Test
    void updateStreak_alternatingDirections_resetsProperly() throws IOException {
        LocalDate day1 = LocalDate.of(2026, 3, 15);
        LocalDate day2 = LocalDate.of(2026, 3, 16);
        LocalDate day3 = LocalDate.of(2026, 3, 17);
        LocalDate day4 = LocalDate.of(2026, 3, 18);
        LocalDate day5 = LocalDate.of(2026, 3, 19);

        persistence.updateStreak("XLK", true, day1); // 1 outperforming
        persistence.updateStreak("XLK", true, day2); // 2 outperforming
        persistence.updateStreak("XLK", false, day3); // 1 underperforming (reset)
        persistence.updateStreak("XLK", false, day4); // 2 underperforming
        persistence.updateStreak("XLK", true, day5); // 1 outperforming (reset)

        SectorRsStreak streak = persistence.getStreak("XLK").orElseThrow();
        assertEquals(1, streak.streakDays());
        assertTrue(streak.isOutperforming());
    }
}
