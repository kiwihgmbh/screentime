package com.kiwih.screentime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwih.screentime.domain.AuditAction;
import com.kiwih.screentime.domain.AuditLog;
import com.kiwih.screentime.repo.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Writes the trail. Every create, update and delete on a session or an
 * adjustment lands here, and so does every change to a check, a setting, a
 * week flag or an account, because those change what a number means too.
 *
 * The old and new values are stored as JSON. Nothing reads them back
 * programmatically; they exist so a parent can answer "why is this number
 * different from yesterday" a month later.
 */
@Service
public class AuditService {

    public static final String SESSION = "SESSION";
    public static final String ADJUSTMENT = "ADJUSTMENT";
    public static final String WEEKLY_CHECK = "WEEKLY_CHECK";
    public static final String SETTING = "SETTING";
    public static final String WEEK_FLAG = "WEEK_FLAG";
    public static final String USER = "USER";

    private final AuditLogRepository auditLog;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLog, ObjectMapper objectMapper, Clock clock) {
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void created(String entity, Long entityId, Object newValue, Long userId) {
        write(entity, entityId, AuditAction.CREATE, null, newValue, userId);
    }

    public void updated(String entity, Long entityId, Object oldValue, Object newValue, Long userId) {
        write(entity, entityId, AuditAction.UPDATE, oldValue, newValue, userId);
    }

    public void deleted(String entity, Long entityId, Object oldValue, Long userId) {
        write(entity, entityId, AuditAction.DELETE, oldValue, null, userId);
    }

    private void write(String entity, Long entityId, AuditAction action,
                       Object oldValue, Object newValue, Long userId) {
        auditLog.save(new AuditLog(entity, entityId, action,
                asJson(oldValue), asJson(newValue), userId, clock.instant()));
    }

    private String asJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // an audit row that cannot be written is worse than an ugly one
            return "\"unserialisable: " + value.getClass().getSimpleName() + "\"";
        }
    }
}
