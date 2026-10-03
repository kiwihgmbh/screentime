package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** The latest entries for one kind of thing, for the change log on the settings page. */
    List<AuditLog> findTop100ByEntityOrderByAtDesc(String entity);
}
