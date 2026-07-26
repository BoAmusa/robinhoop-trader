package com.robinhoop.trader.scheduler;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketHoursTest {

    private static final ZoneId ET = ZoneId.of("America/New_York");

    @Test
    void openDuringRegularSessionOnAWeekday() {
        // Wednesday, 2026-07-22, 11:00 ET
        assertTrue(MarketHours.isOpen(ZonedDateTime.of(2026, 7, 22, 11, 0, 0, 0, ET)));
    }

    @Test
    void closedBeforeOpen() {
        assertFalse(MarketHours.isOpen(ZonedDateTime.of(2026, 7, 22, 9, 0, 0, 0, ET)));
    }

    @Test
    void closedAtOrAfterClose() {
        assertFalse(MarketHours.isOpen(ZonedDateTime.of(2026, 7, 22, 16, 0, 0, 0, ET)));
    }

    @Test
    void closedOnWeekends() {
        // Saturday, 2026-07-25, midday
        assertFalse(MarketHours.isOpen(ZonedDateTime.of(2026, 7, 25, 12, 0, 0, 0, ET)));
    }
}
