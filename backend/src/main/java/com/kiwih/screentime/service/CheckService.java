package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.*;
import com.kiwih.screentime.repo.AdjustmentRepository;
import com.kiwih.screentime.repo.DeviceRepository;
import com.kiwih.screentime.repo.WeeklyCheckRepository;
import com.kiwih.screentime.rules.CheckOutcome;
import com.kiwih.screentime.rules.RuleViolation;
import com.kiwih.screentime.rules.ScreentimeRules;
import com.kiwih.screentime.security.AppPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Sunday comparison of the log against what the devices report.
 *
 * Running a check twice for the same week must not punish twice. The second
 * check replaces the first, and the adjustment the first one wrote is deleted
 * before the new one is written, so nothing stacks.
 */
@Service
public class CheckService {

    private final WeeklyCheckRepository checks;
    private final AdjustmentRepository adjustments;
    private final DeviceRepository devices;
    private final SettingsService settingsService;
    private final BalanceService balances;
    private final AccountResolver accounts;
    private final WeekService weeks;
    private final AuditService audit;
    private final Clock clock;

    public CheckService(WeeklyCheckRepository checks, AdjustmentRepository adjustments,
                        DeviceRepository devices, SettingsService settingsService,
                        BalanceService balances, AccountResolver accounts, WeekService weeks,
                        AuditService audit, Clock clock) {
        this.checks = checks;
        this.adjustments = adjustments;
        this.devices = devices;
        this.settingsService = settingsService;
        this.balances = balances;
        this.accounts = accounts;
        this.weeks = weeks;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<WeeklyCheck> find(LocalDate weekStart) {
        return checks.findByWeekStart(weekStart);
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public Result save(AppPrincipal caller, LocalDate requestedWeekStart,
                       List<ReportedDevice> reported, boolean deliberate) {
        ScreentimeRules rules = settingsService.rules();
        LocalDate weekStart = rules.calendar().weekStartOf(requestedWeekStart);
        User account = accounts.resolve(caller);

        validateReported(reported);

        int logged = balances.loggedFunMinutes(account.getId(), weekStart, rules.calendar());
        int reportedTotal = reported.stream().mapToInt(ReportedDevice::getMinutes).sum();
        CheckOutcome outcome = rules.weeklyCheck(logged, reportedTotal, deliberate);
        LocalDate followingWeek = rules.followingWeek(weekStart);

        replacePreviousCheck(weekStart, caller.userId());

        WeeklyCheck check = checks.save(new WeeklyCheck(
                weekStart, logged, reportedTotal, outcome.difference(), outcome.penaltyMinutes(),
                outcome.clean(), deliberate, caller.userId(), clock.instant(), reported));
        audit.created(AuditService.WEEKLY_CHECK, check.getId(), Map.of(
                "weekStart", weekStart.toString(),
                "logged", logged,
                "reported", reportedTotal,
                "difference", outcome.difference(),
                "penalty", outcome.penaltyMinutes(),
                "clean", outcome.clean(),
                "deliberate", deliberate), caller.userId());

        Adjustment penalty = null;
        if (outcome.penaltyMinutes() > 0) {
            penalty = new Adjustment(followingWeek, outcome.adjustmentMinutes(),
                    reasonFor(outcome, deliberate, weekStart), caller.userId(), clock.instant());
            penalty.setCheckId(check.getId());
            penalty = adjustments.save(penalty);
            audit.created(AuditService.ADJUSTMENT, penalty.getId(), Map.of(
                    "weekStart", followingWeek.toString(),
                    "minutes", penalty.getMinutes(),
                    "reason", penalty.getReason(),
                    "checkId", check.getId()), caller.userId());
        }

        // a clean week sets the bonus for the following one, and a week that is
        // not clean clears it again, so redoing a check cannot leave a stale bonus
        weeks.setBonusActive(followingWeek, outcome.bonusForFollowingWeek(), caller.userId());

        return new Result(check, outcome, followingWeek, penalty);
    }

    /**
     * Removes the previous check for this week together with the adjustment it
     * wrote. Both deletions are audited, so the reversal is as visible as the
     * penalty was.
     */
    private void replacePreviousCheck(LocalDate weekStart, Long byUserId) {
        checks.findByWeekStart(weekStart).ifPresent(previous -> {
            for (Adjustment written : adjustments.findByCheckId(previous.getId())) {
                adjustments.delete(written);
                audit.deleted(AuditService.ADJUSTMENT, written.getId(), Map.of(
                        "weekStart", written.getWeekStart().toString(),
                        "minutes", written.getMinutes(),
                        "reason", written.getReason(),
                        "reversedCheckId", previous.getId()), byUserId);
            }
            checks.delete(previous);
            audit.deleted(AuditService.WEEKLY_CHECK, previous.getId(), Map.of(
                    "weekStart", previous.getWeekStart().toString(),
                    "difference", previous.getDifference(),
                    "penalty", previous.getPenaltyMinutes(),
                    "clean", previous.isClean()), byUserId);
        });
        // the new row reuses the unique week, so the delete has to reach the
        // database before the insert
        checks.flush();
        adjustments.flush();
    }

    private void validateReported(List<ReportedDevice> reported) {
        if (reported == null || reported.isEmpty()) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "A check needs the minutes each device reported, even if that is zero.");
        }
        Set<Long> seen = new HashSet<>();
        for (ReportedDevice entry : reported) {
            if (entry.getMinutes() < 0) {
                throw new RuleViolation(RuleViolation.Kind.INVALID,
                        "A device cannot report negative minutes.");
            }
            if (!seen.add(entry.getDeviceId())) {
                throw new RuleViolation(RuleViolation.Kind.INVALID,
                        "Device " + entry.getDeviceId() + " is listed twice.");
            }
            if (!devices.existsById(entry.getDeviceId())) {
                throw new NotFoundException("Device", entry.getDeviceId());
            }
        }
    }

    private static String reasonFor(CheckOutcome outcome, boolean deliberate, LocalDate checkedWeek) {
        StringBuilder reason = new StringBuilder("Weekly check of ").append(checkedWeek);
        if (outcome.difference() > 0) {
            reason.append(": ").append(outcome.difference())
                    .append(" minutes more on the devices than in the log");
        }
        if (deliberate) {
            reason.append(outcome.difference() > 0 ? ", " : ": ").append("rules worked around on purpose");
        }
        return reason.toString();
    }

    public record Result(WeeklyCheck check, CheckOutcome outcome,
                         LocalDate followingWeek, Adjustment penalty) {
    }
}
