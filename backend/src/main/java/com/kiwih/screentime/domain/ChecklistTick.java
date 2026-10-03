package com.kiwih.screentime.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One item the child ticked for one start of screen time, with the text as it
 * read then. The time is the server's, like every other instant here.
 */
@Entity
@Table(name = "checklist_ticks")
public class ChecklistTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "item_text", nullable = false, length = 200)
    private String itemText;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "ticked_at", nullable = false)
    private Instant tickedAt;

    protected ChecklistTick() {
    }

    public ChecklistTick(Long sessionId, Long itemId, String itemText, Long userId, Instant tickedAt) {
        this.sessionId = sessionId;
        this.itemId = itemId;
        this.itemText = itemText;
        this.userId = userId;
        this.tickedAt = tickedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getItemText() {
        return itemText;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getTickedAt() {
        return tickedAt;
    }
}
