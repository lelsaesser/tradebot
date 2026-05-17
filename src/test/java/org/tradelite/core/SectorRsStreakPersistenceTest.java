package org.tradelite.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.repository.SectorRsStreakRepository;

@ExtendWith(MockitoExtension.class)
class SectorRsStreakPersistenceTest {

    @Mock private SectorRsStreakRepository sectorRsStreakRepository;

    private SectorRsStreakPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new SectorRsStreakPersistence(sectorRsStreakRepository);
    }

    @Test
    void updateStreak_newSector_createsStreakWithDayOne() {
        LocalDate today = LocalDate.of(2026, 3, 19);
        when(sectorRsStreakRepository.findBySymbol("XLK")).thenReturn(Optional.empty());

        SectorRsStreakPersistence.StreakUpdateResult result =
                persistence.updateStreak("XLK", true, today);

        assertEquals("XLK", result.newStreak().symbol());
        assertEquals(1, result.newStreak().streakDays());
        assertTrue(result.newStreak().isOutperforming());
        assertEquals(today, result.newStreak().lastUpdated());
        assertFalse(result.directionChanged());
        assertEquals(0, result.previousStreakDays());
        verify(sectorRsStreakRepository).save(result.newStreak());
    }

    @Test
    void updateStreak_sameDirectionNextDay_incrementsStreak() {
        LocalDate day1 = LocalDate.of(2026, 3, 18);
        LocalDate day2 = LocalDate.of(2026, 3, 19);
        SectorRsStreak existing = new SectorRsStreak("XLK", 1, true, day1);
        when(sectorRsStreakRepository.findBySymbol("XLK")).thenReturn(Optional.of(existing));

        SectorRsStreakPersistence.StreakUpdateResult result =
                persistence.updateStreak("XLK", true, day2);

        assertEquals(2, result.newStreak().streakDays());
        assertTrue(result.newStreak().isOutperforming());
        assertFalse(result.directionChanged());
        assertEquals(0, result.previousStreakDays());
        verify(sectorRsStreakRepository).save(result.newStreak());
    }

    @Test
    void updateStreak_directionChange_resetsStreak() {
        LocalDate day1 = LocalDate.of(2026, 3, 18);
        LocalDate day2 = LocalDate.of(2026, 3, 19);
        SectorRsStreak existing = new SectorRsStreak("XLK", 1, true, day1);
        when(sectorRsStreakRepository.findBySymbol("XLK")).thenReturn(Optional.of(existing));

        SectorRsStreakPersistence.StreakUpdateResult result =
                persistence.updateStreak("XLK", false, day2);

        assertEquals(1, result.newStreak().streakDays());
        assertFalse(result.newStreak().isOutperforming());
        assertTrue(result.directionChanged());
        assertEquals(1, result.previousStreakDays());
        verify(sectorRsStreakRepository).save(result.newStreak());
    }

    @Test
    void updateStreak_sameDay_returnsExistingStreak() {
        LocalDate today = LocalDate.of(2026, 3, 19);
        SectorRsStreak existing = new SectorRsStreak("XLK", 1, true, today);
        when(sectorRsStreakRepository.findBySymbol("XLK")).thenReturn(Optional.of(existing));

        SectorRsStreakPersistence.StreakUpdateResult result =
                persistence.updateStreak("XLK", true, today);

        assertEquals(1, result.newStreak().streakDays());
        assertFalse(result.directionChanged());
        verify(sectorRsStreakRepository, never()).save(any());
    }

    @Test
    void getStreak_unknownSymbol_returnsEmpty() {
        when(sectorRsStreakRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        Optional<SectorRsStreak> streak = persistence.getStreak("UNKNOWN");

        assertTrue(streak.isEmpty());
    }

    @Test
    void getStreak_existingSymbol_returnsStreak() {
        LocalDate today = LocalDate.of(2026, 3, 19);
        SectorRsStreak expected = new SectorRsStreak("XLK", 5, true, today);
        when(sectorRsStreakRepository.findBySymbol("XLK")).thenReturn(Optional.of(expected));

        Optional<SectorRsStreak> streak = persistence.getStreak("XLK");

        assertTrue(streak.isPresent());
        assertEquals("XLK", streak.get().symbol());
        assertEquals(5, streak.get().streakDays());
    }

    @Test
    void getAllStreaks_noData_returnsEmptyMap() {
        when(sectorRsStreakRepository.findAll()).thenReturn(Map.of());

        Map<String, SectorRsStreak> streaks = persistence.getAllStreaks();

        assertTrue(streaks.isEmpty());
    }

    @Test
    void getAllStreaks_withData_returnsAllStreaks() {
        LocalDate today = LocalDate.of(2026, 3, 19);
        Map<String, SectorRsStreak> expected = new HashMap<>();
        expected.put("XLK", new SectorRsStreak("XLK", 3, true, today));
        expected.put("XLU", new SectorRsStreak("XLU", 2, false, today));
        expected.put("XLF", new SectorRsStreak("XLF", 1, true, today));
        when(sectorRsStreakRepository.findAll()).thenReturn(expected);

        Map<String, SectorRsStreak> streaks = persistence.getAllStreaks();

        assertEquals(3, streaks.size());
        assertTrue(streaks.containsKey("XLK"));
        assertTrue(streaks.containsKey("XLU"));
        assertTrue(streaks.containsKey("XLF"));
    }

    @Test
    void updateStreak_directionChange_returnsPreviousStreakDays() {
        LocalDate day4 = LocalDate.of(2026, 3, 18);
        LocalDate day5 = LocalDate.of(2026, 3, 19);
        SectorRsStreak existing = new SectorRsStreak("XLK", 4, true, day4);
        when(sectorRsStreakRepository.findBySymbol("XLK")).thenReturn(Optional.of(existing));

        SectorRsStreakPersistence.StreakUpdateResult result =
                persistence.updateStreak("XLK", false, day5);

        assertTrue(result.directionChanged());
        assertEquals(4, result.previousStreakDays());
        assertEquals(1, result.newStreak().streakDays());
        assertFalse(result.newStreak().isOutperforming());
    }
}
