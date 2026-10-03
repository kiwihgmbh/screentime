package com.kiwih.screentime.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Append only. Every create, update and delete on a session or adjustment lands here. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String entity;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditAction action;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "at", nullable = false)
    private Instant at = Instant.now();

    protected AuditLog() {
    }

    public AuditLog(String entity, Long entityId, AuditAction action,
                    String oldValue, String newValue, Long userId, Instant at) {
        this.entity = entity;
        this.entityId = entityId;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.userId = userId;
        this.at = at;
    }

    public Long getId() {
        return id;
    }

    public String getEntity() {
        return entity;
    }

    public Long getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getAt() {
        return at;
    }
}
