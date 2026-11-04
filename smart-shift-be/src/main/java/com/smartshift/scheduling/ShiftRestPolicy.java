package com.smartshift.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Branch-local split shifts are exempt from inter-shift rest, never from overlap/hour limits. */
public final class ShiftRestPolicy {
    private ShiftRestPolicy() {}

    public static boolean overlaps(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }

    public static boolean sameDaySplit(Instant firstStart, Instant firstEnd, Instant secondStart,
                                       Instant secondEnd, ZoneId zone) {
        LocalDate day = firstStart.atZone(zone).toLocalDate();
        // An overnight shift (including one ending exactly at midnight) never receives this exemption.
        return firstEnd.atZone(zone).toLocalDate().equals(day)
            && secondStart.atZone(zone).toLocalDate().equals(day)
            && secondEnd.atZone(zone).toLocalDate().equals(day);
    }

    public static boolean insufficientRest(Instant firstStart, Instant firstEnd, Instant secondStart,
                                           Instant secondEnd, long restSeconds, ZoneId zone) {
        if (overlaps(firstStart, firstEnd, secondStart, secondEnd)
                || sameDaySplit(firstStart, firstEnd, secondStart, secondEnd, zone)) return false;
        Duration gap = !firstEnd.isAfter(secondStart)
            ? Duration.between(firstEnd, secondStart) : Duration.between(secondEnd, firstStart);
        return gap.compareTo(Duration.ofSeconds(restSeconds)) < 0;
    }

    public static boolean conflicts(Instant firstStart, Instant firstEnd, Instant secondStart,
                                    Instant secondEnd, long restSeconds, ZoneId zone) {
        return overlaps(firstStart, firstEnd, secondStart, secondEnd)
            || insufficientRest(firstStart, firstEnd, secondStart, secondEnd, restSeconds, zone);
    }
}
