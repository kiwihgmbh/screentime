package com.kiwih.screentime.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * The per week switch the weekly check sets. {@code bonusActive} is set by the
 * check of the previous week and is never cumulative: it is on or off for this
 * week. Holidays are periods now, in {@link HolidayPeriod}.
 */
@Entity
@Table(name = "week_flags")
public class WeekFlag {

    @Id
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "bonus_active", nullable = false)
    private boolean bonusActive;

    protected WeekFlag() {
    }

    public WeekFlag(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public boolean isBonusActive() {
        return bonusActive;
    }

    public void setBonusActive(boolean bonusActive) {
        this.bonusActive = bonusActive;
    }
}
