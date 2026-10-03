package com.kiwih.screentime.web.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** One day of a week as the rules see it: a holiday day or not, and its ceiling, bonus included. */
public record EffectiveDayView(LocalDate date, DayOfWeek dayOfWeek, boolean holiday, int capMinutes) {
}
