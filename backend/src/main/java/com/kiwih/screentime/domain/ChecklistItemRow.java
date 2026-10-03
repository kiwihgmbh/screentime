package com.kiwih.screentime.domain;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A reminder as stored. The rule lives in rules.ChecklistItem; this is the
 * row behind it. Removing an item only switches it off, so the ticks that
 * point at it stay readable.
 */
@Entity
@Table(name = "checklist_items")
public class ChecklistItemRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String text;

    @Column(length = 64)
    private String weekdays;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChecklistItemRow() {
    }

    public ChecklistItemRow(String text, Set<DayOfWeek> weekdays, LocalDate validFrom, LocalDate validUntil,
                            int sortOrder, Long createdBy, Instant createdAt) {
        change(text, weekdays, validFrom, validUntil, sortOrder);
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void change(String text, Set<DayOfWeek> weekdays, LocalDate validFrom, LocalDate validUntil,
                       int sortOrder) {
        this.text = text;
        this.weekdays = weekdays == null || weekdays.isEmpty() ? null
                : EnumSet.copyOf(weekdays).stream().map(DayOfWeek::name).collect(Collectors.joining(","));
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.sortOrder = sortOrder;
    }

    public void remove() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public Set<DayOfWeek> getWeekdays() {
        if (weekdays == null || weekdays.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(weekdays.split(",")).map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
