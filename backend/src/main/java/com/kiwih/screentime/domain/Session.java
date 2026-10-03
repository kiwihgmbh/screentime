package com.kiwih.screentime.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One stretch of screen time. A session belongs to the local day on which it
 * started, even if it ends after midnight or after the cut off hour.
 *
 * {@code startedAt} and {@code endedAt} are always set by the server. The
 * duration is stored, in seconds, because a manual entry has no real clock
 * behind it, because an automatically closed session is capped below its real
 * length, and because a parent may correct the duration afterwards.
 */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private SessionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private SessionSource source;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "auto_closed", nullable = false)
    private boolean autoClosed;

    @Column(length = 512)
    private String note;

    protected Session() {
    }

    /** An open timer session. */
    public static Session startTimer(Long userId, Long deviceId, SessionType type, Long createdBy, Instant startedAt) {
        Session s = new Session();
        s.userId = userId;
        s.deviceId = deviceId;
        s.type = type;
        s.source = SessionSource.TIMER;
        s.createdBy = createdBy;
        s.startedAt = startedAt;
        return s;
    }

    /** A completed entry booked by hand. */
    public static Session manual(Long userId, Long deviceId, SessionType type, Long createdBy,
                                 Instant startedAt, int minutes, String note) {
        Session s = new Session();
        s.userId = userId;
        s.deviceId = deviceId;
        s.type = type;
        s.source = SessionSource.MANUAL;
        s.createdBy = createdBy;
        s.startedAt = startedAt;
        s.durationSeconds = Math.multiplyExact(minutes, 60);
        s.endedAt = startedAt.plusSeconds(s.durationSeconds);
        s.note = note;
        return s;
    }

    public void close(Instant endedAt, int seconds, boolean autoClosed) {
        this.endedAt = endedAt;
        this.durationSeconds = seconds;
        this.autoClosed = autoClosed;
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    /** Seconds already counted. An open session counts nothing until it is closed. */
    public int countedSeconds() {
        return durationSeconds == null ? 0 : durationSeconds;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public SessionType getType() {
        return type;
    }

    public void setType(SessionType type) {
        this.type = type;
    }

    public SessionSource getSource() {
        return source;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public boolean isAutoClosed() {
        return autoClosed;
    }

    public void setAutoClosed(boolean autoClosed) {
        this.autoClosed = autoClosed;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
