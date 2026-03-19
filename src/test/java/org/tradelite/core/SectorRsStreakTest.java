package org.tradelite.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SectorRsStreakTest {

    @Test
    void newStreak_createsStreakWithDayOne() {
        LocalDate today = LocalDate.of(2026, 3, 19);

        SectorRsStreak streak = SectorRsStreak.newStreak("XLK", true, today);

        assertEquals("XLK", streak.symbol());
        assertEquals(1, streak.streakDays());
        assertTrue(streak.isOutperforming());
        assertEquals(today, streak.lastUpdated());
    }

    @Test
    void newStreak_underperforming_createsCorrectStreak() {
        LocalDate today = LocalDate.of(2026, 3, 19);

        SectorRsStreak streak = SectorRsStreak.newStreak("XLU", false, today);

        assertEquals("XLU", streak.symbol());
        assertEquals(1, streak.streakDays());
        assertFalse(streak.isOutperforming());
    }

    @Test
    void update_sameDirection_incrementsStreak() {
        LocalDate day1 = LocalDate.of(2026, 3, 18);
        LocalDate day2 = LocalDate.of(2026, 3, 19);

        SectorRsStreak streak = SectorRsStreak.newStreak("XLK", true, day1);
        SectorRsStreak updated = streak.update(true, day2);

        assertEquals(2, updated.streakDays());
        assertTrue(updated.isOutperforming());
        assertEquals(day2, updated.lastUpdated());
    }

    @Test
    void update_directionChanged_resetsStreakToOne() {
        LocalDate day1 = LocalDate.of(2026, 3, 18);
        LocalDate day2 = LocalDate.of(2026, 3, 19);

        SectorRsStreak streak = new SectorRsStreak("XLK", 5, true, day1);
        SectorRsStreak updated = streak.update(false, day2);

        assertEquals(1, updated.streakDays());
        assertFalse(updated.isOutperforming());
        assertEquals(day2, updated.lastUpdated());
    }

    @Test
    void update_multipleDaysSameDirection_accumulates() {
        LocalDate day1 = LocalDate.of(2026, 3, 16);
        LocalDate day2 = LocalDate.of(2026, 3, 17);
        LocalDate day3 = LocalDate.of(2026, 3, 18);
        LocalDate day4 = LocalDate.of(2026, 3, 19);

        SectorRsStreak streak = SectorRsStreak.newStreak("XLF", true, day1);
        streak = streak.update(true, day2);
        streak = streak.update(true, day3);
        streak = streak.update(true, day4);

        assertEquals(4, streak.streakDays());
        assertTrue(streak.isOutperforming());
    }

    @Test
    void update_fromUnderperformingToOutperforming_resetsStreak() {
        LocalDate day1 = LocalDate.of(2026, 3, 18);
        LocalDate day2 = LocalDate.of(2026, 3, 19);

        SectorRsStreak streak = new SectorRsStreak("XLE", 10, false, day1);
        SectorRsStreak updated = streak.update(true, day2);

        assertEquals(1, updated.streakDays());
        assertTrue(updated.isOutperforming());
    }
}
