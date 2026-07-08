package com.linkride.backend.ride;

import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Optional recurrence for a ride. Stored as {@code jsonb} on {@link Ride} (see
 * {@code repeat_schedule} column) and reused as-is for the API request/response shape —
 * it carries no JPA-specific annotations, so there's nothing to isolate a DTO from.
 */
@Data
public class RepeatSchedule {

    private RepeatFrequency frequency;

    /** Only meaningful when {@code frequency == WEEKLY}. */
    private List<DayOfWeek> daysOfWeek;

    /** Null means the recurrence has no end date. */
    private LocalDate endDate;
}
