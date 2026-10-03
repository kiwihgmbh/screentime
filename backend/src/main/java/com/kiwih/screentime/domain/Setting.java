package com.kiwih.screentime.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One value of one setting in one scope, in force from a Monday on. Settings
 * are rows, never constants in code, and a row is never changed: a new value
 * is a new row, so the values that applied to any past week can always be
 * read back. The table rejects an update, so this entity has no setters.
 */
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String scope;

    @Column(name = "key", nullable = false, length = 64)
    private String key;

    @Column(name = "value", nullable = false, length = 256)
    private String value;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Setting() {
    }

    public Setting(String scope, String key, String value, LocalDate validFrom, Long createdBy, Instant createdAt) {
        this.scope = scope;
        this.key = key;
        this.value = value;
        this.validFrom = validFrom;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getScope() {
        return scope;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
