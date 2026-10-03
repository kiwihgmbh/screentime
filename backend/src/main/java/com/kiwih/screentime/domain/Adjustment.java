package com.kiwih.screentime.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A signed correction to one week's budget, always with a reason the child can
 * read. A negative value is a deduction, a positive one a gift.
 */
@Entity
@Table(name = "adjustments")
public class Adjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false)
    private int minutes;

    @Column(nullable = false, length = 256)
    private String reason;

    /** Set when the weekly check wrote this row, so a replacement check can reverse it. */
    @Column(name = "check_id")
    private Long checkId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Adjustment() {
    }

    public Adjustment(LocalDate weekStart, int minutes, String reason, Long createdBy, Instant createdAt) {
        this.weekStart = weekStart;
        this.minutes = minutes;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public int getMinutes() {
        return minutes;
    }

    public String getReason() {
        return reason;
    }

    public Long getCheckId() {
        return checkId;
    }

    public void setCheckId(Long checkId) {
        this.checkId = checkId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
