package com.smartshift.scheduling;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class ShiftRestPolicyTest {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long REST = 11 * 3600;

    private Instant at(String local) { return LocalDateTime.parse(local).atZone(VIETNAM).toInstant(); }
    private boolean conflicts(String a, String b, String c, String d) {
        Instant start = at(a), end = at(b), next = at(c), last = at(d);
        boolean result = ShiftRestPolicy.conflicts(start, end, next, last, REST, VIETNAM);
        assertEquals(result, ShiftRestPolicy.conflicts(next, last, start, end, REST, VIETNAM));
        return result;
    }

    @Test void allowsSameLocalDayEvenWhenUtcDatesDiffer() {
        assertFalse(conflicts("2030-01-07T04:00", "2030-01-07T08:00", "2030-01-07T15:00", "2030-01-07T19:00"));
    }
    @Test void allowsTouchingDaytimeShiftsButNeverOverlaps() {
        assertFalse(conflicts("2030-01-07T08:00", "2030-01-07T12:00", "2030-01-07T12:00", "2030-01-07T16:00"));
        assertTrue(conflicts("2030-01-07T08:00", "2030-01-07T12:00", "2030-01-07T11:59", "2030-01-07T16:00"));
    }
    @Test void blocksClopeningAcrossLocalDaysEvenWhenUtcDateIsTheSame() {
        assertTrue(conflicts("2030-01-07T20:00", "2030-01-07T23:00", "2030-01-08T05:00", "2030-01-08T09:00"));
    }
    @Test void overnightShiftCannotBecomeSplitShiftOnItsEndDateOrStartDate() {
        assertTrue(conflicts("2030-01-07T22:00", "2030-01-08T06:00", "2030-01-08T07:00", "2030-01-08T11:00"));
        assertTrue(conflicts("2030-01-07T08:00", "2030-01-07T12:00", "2030-01-07T22:00", "2030-01-08T06:00"));
        assertTrue(conflicts("2030-01-07T20:00", "2030-01-08T00:00", "2030-01-08T07:00", "2030-01-08T11:00"));
    }
    @Test void acceptsExactlyMinimumRestButRejectsOneNanosecondLess() {
        Instant start=at("2030-01-07T22:00"), end=at("2030-01-08T06:00"), next=end.plusSeconds(REST);
        assertFalse(ShiftRestPolicy.conflicts(start,end,next,next.plusSeconds(3600),REST,VIETNAM));
        assertTrue(ShiftRestPolicy.conflicts(start,end,next.minusNanos(1),next.plusSeconds(3600),REST,VIETNAM));
    }
    @Test void dstRestUsesElapsedTimeInsteadOfWallClockDifference() {
        ZoneId zone=ZoneId.of("America/New_York");
        Instant start=ZonedDateTime.of(2030,3,9,18,0,0,0,zone).toInstant();
        Instant end=ZonedDateTime.of(2030,3,9,22,0,0,0,zone).toInstant();
        Instant next=ZonedDateTime.of(2030,3,10,9,0,0,0,zone).toInstant();
        assertEquals(Duration.ofHours(10),Duration.between(end,next));
        assertTrue(ShiftRestPolicy.conflicts(start,end,next,next.plusSeconds(3600),REST,zone));
    }
}
