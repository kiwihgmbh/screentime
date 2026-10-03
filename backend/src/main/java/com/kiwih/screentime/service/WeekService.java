package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.WeekFlag;
import com.kiwih.screentime.repo.WeekFlagRepository;
import com.kiwih.screentime.rules.WeekState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * The per week bonus switch. A week with no row is a plain week, so nothing has
 * to be created in advance. Holidays are periods, read by SettingsService.
 */
@Service
public class WeekService {

    private final WeekFlagRepository weekFlags;
    private final AuditService audit;

    public WeekService(WeekFlagRepository weekFlags, AuditService audit) {
        this.weekFlags = weekFlags;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public WeekState state(LocalDate weekStart) {
        return weekFlags.findById(weekStart)
                .map(f -> new WeekState(weekStart, f.isBonusActive()))
                .orElseGet(() -> WeekState.plain(weekStart));
    }

    /**
     * Sets or clears the bonus for a week. Called by the weekly check of the
     * previous week, never by hand, and never cumulatively: the bonus is on or
     * off for a week and nothing more.
     */
    @Transactional
    public void setBonusActive(LocalDate weekStart, boolean bonusActive, Long byUserId) {
        WeekFlag flag = weekFlags.findById(weekStart).orElseGet(() -> new WeekFlag(weekStart));
        boolean before = flag.isBonusActive();
        if (before == bonusActive) {
            weekFlags.save(flag);
            return;
        }
        flag.setBonusActive(bonusActive);
        weekFlags.save(flag);
        audit.updated(AuditService.WEEK_FLAG, null,
                Map.of("weekStart", weekStart.toString(), "bonusActive", before),
                Map.of("weekStart", weekStart.toString(), "bonusActive", bonusActive),
                byUserId);
    }
}
