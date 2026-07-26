package com.robinhoop.trader.scheduler;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Regular US equity market session, Eastern time. Does NOT account for market holidays
 * (Thanksgiving, Christmas, etc.) or early-close days — the scheduler will attempt to
 * run on those days and simply find nothing tradable; harmless but worth knowing.
 */
public final class MarketHours {

    private static final ZoneId EXCHANGE_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime OPEN = LocalTime.of(9, 30);
    private static final LocalTime CLOSE = LocalTime.of(16, 0);

    private MarketHours() {
    }

    public static boolean isOpen() {
        return isOpen(ZonedDateTime.now(EXCHANGE_ZONE));
    }

    public static boolean isOpen(ZonedDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(OPEN) && time.isBefore(CLOSE);
    }
}
