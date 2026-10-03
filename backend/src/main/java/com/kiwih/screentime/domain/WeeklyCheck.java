package com.kiwih.screentime.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The Sunday comparison of the log against what the devices report. One per
 * week: a second check for the same week replaces the first and reverses the
 * adjustment the first one wrote, so penalties never stack.
 */
@Entity
@Table(name = "weekly_checks")
public class WeeklyCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start", nullable = false, unique = true)
    private LocalDate weekStart;

    @Column(name = "logged_minutes", nullable = false)
    private int loggedMinutes;

    @Column(name = "reported_minutes", nullable = false)
    private int reportedMinutes;

    /** reported minus logged. Positive means time is missing from the log. */
    @Column(nullable = false)
    private int difference;

    @Column(name = "penalty_minutes", nullable = false)
    private int penaltyMinutes;

    @Column(nullable = false)
    private boolean clean;

    @Column(nullable = false)
    private boolean deliberate;

    @Column(name = "checked_by", nullable = false)
    private Long checkedBy;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "weekly_check_devices", joinColumns = @JoinColumn(name = "check_id"))
    private List<ReportedDevice> reported = new ArrayList<>();

    protected WeeklyCheck() {
    }

    public WeeklyCheck(LocalDate weekStart, int loggedMinutes, int reportedMinutes, int difference,
                       int penaltyMinutes, boolean clean, boolean deliberate,
                       Long checkedBy, Instant checkedAt, List<ReportedDevice> reported) {
        this.weekStart = weekStart;
        this.loggedMinutes = loggedMinutes;
        this.reportedMinutes = reportedMinutes;
        this.difference = difference;
        this.penaltyMinutes = penaltyMinutes;
        this.clean = clean;
        this.deliberate = deliberate;
        this.checkedBy = checkedBy;
        this.checkedAt = checkedAt;
        this.reported = new ArrayList<>(reported);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public int getLoggedMinutes() {
        return loggedMinutes;
    }

    public int getReportedMinutes() {
        return reportedMinutes;
    }

    public int getDifference() {
        return difference;
    }

    public int getPenaltyMinutes() {
        return penaltyMinutes;
    }

    public boolean isClean() {
        return clean;
    }

    public boolean isDeliberate() {
        return deliberate;
    }

    public Long getCheckedBy() {
        return checkedBy;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public List<ReportedDevice> getReported() {
        return reported;
    }
}
