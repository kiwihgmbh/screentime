package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.Adjustment;
import com.kiwih.screentime.repo.AdjustmentRepository;
import com.kiwih.screentime.rules.RuleViolation;
import com.kiwih.screentime.security.AppPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Signed corrections to a week's budget. The reason is required and not
 * decorative: a balance that drops without an explanation is the fastest way to
 * lose the child's cooperation.
 */
@Service
public class AdjustmentService {

    private final AdjustmentRepository adjustments;
    private final SettingsService settingsService;
    private final AuditService audit;
    private final Clock clock;

    public AdjustmentService(AdjustmentRepository adjustments, SettingsService settingsService,
                             AuditService audit, Clock clock) {
        this.adjustments = adjustments;
        this.settingsService = settingsService;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public Adjustment create(AppPrincipal caller, LocalDate weekStart, int minutes, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "An adjustment needs a reason the child can read.");
        }
        if (minutes == 0) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "An adjustment of zero minutes changes nothing.");
        }
        LocalDate monday = settingsService.rules().calendar().weekStartOf(weekStart);
        Adjustment saved = adjustments.save(new Adjustment(
                monday, minutes, reason.trim(), caller.userId(), clock.instant()));
        audit.created(AuditService.ADJUSTMENT, saved.getId(), snapshot(saved), caller.userId());
        return saved;
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public void delete(AppPrincipal caller, Long id) {
        Adjustment adjustment = adjustments.findById(id)
                .orElseThrow(() -> new NotFoundException("Adjustment", id));
        if (adjustment.getCheckId() != null) {
            throw new RuleViolation(RuleViolation.Kind.NOT_ALLOWED,
                    "This adjustment belongs to a weekly check. Redo the check instead.");
        }
        adjustments.delete(adjustment);
        audit.deleted(AuditService.ADJUSTMENT, id, snapshot(adjustment), caller.userId());
    }

    private static Object snapshot(Adjustment a) {
        return new Snapshot(a.getId(), a.getWeekStart().toString(), a.getMinutes(),
                a.getReason(), a.getCheckId());
    }

    private record Snapshot(Long id, String weekStart, int minutes, String reason, Long checkId) {
    }
}
