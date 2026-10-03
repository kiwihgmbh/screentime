package com.kiwih.screentime.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A single configurable value. Settings are rows, never constants in code,
 * so a parent can change them without a deployment.
 */
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Column(name = "key", nullable = false, length = 64)
    private String key;

    @Column(name = "value", nullable = false, length = 256)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    protected Setting() {
    }

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void update(String value, Instant at, Long by) {
        this.value = value;
        this.updatedAt = at;
        this.updatedBy = by;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
