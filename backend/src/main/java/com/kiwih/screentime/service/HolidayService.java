package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.HolidayPeriod;
import com.kiwih.screentime.repo.HolidayPeriodRepository;
import com.kiwih.screentime.rules.Holiday;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The school holidays, as the parents enter them. Every change is audited: a
 * period decides which values a week runs on, so moving one changes numbers
 * the child sees.
 */
@Service
public class HolidayService {

    private static final LocalDate OPEN_START = LocalDate.of(1, 1, 1);
    private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

    private final HolidayPeriodRepository periods;
    private final AuditService audit;
    private final Clock clock;

    public HolidayService(HolidayPeriodRepository periods, AuditService audit, Clock clock) {
        this.periods = periods;
        this.audit = audit;
        this.clock = clock;
    }

    /** Every period sharing a day with the range, or all of them when it is open. */
    @PreAuthorize("hasRole('PARENT')")
    @Transactional(readOnly = true)
    public List<HolidayPeriod> list(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return periods.findAllByOrderByStartDateAsc();
        }
        // LocalDate.MIN and MAX are outside what PostgreSQL can store as a date
        return periods.findOverlapping(from != null ? from : OPEN_START, to != null ? to : OPEN_END);
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public HolidayPeriod create(String name, LocalDate start, LocalDate end, Long byUserId) {
        Holiday candidate = checked(null, name, start, end);
        HolidayPeriod saved = periods.save(new HolidayPeriod(
                candidate.name(), candidate.start(), candidate.end(), byUserId, clock.instant()));
        audit.created(AuditService.HOLIDAY_PERIOD, saved.getId(), snapshot(saved), byUserId);
        return saved;
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public HolidayPeriod update(Long id, String name, LocalDate start, LocalDate end, Long byUserId) {
        HolidayPeriod period = periods.findById(id).orElseThrow(() -> new NotFoundException("Holiday period", id));
        Holiday candidate = checked(id, name, start, end);
        Map<String, Object> before = snapshot(period);
        period.change(candidate.name(), candidate.start(), candidate.end());
        audit.updated(AuditService.HOLIDAY_PERIOD, id, before, snapshot(period), byUserId);
        return period;
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public void delete(Long id, Long byUserId) {
        HolidayPeriod period = periods.findById(id).orElseThrow(() -> new NotFoundException("Holiday period", id));
        periods.delete(period);
        audit.deleted(AuditService.HOLIDAY_PERIOD, id, snapshot(period), byUserId);
    }

    /** The rules refuse a backwards or nameless period, and one that shares a day with another. */
    private Holiday checked(Long id, String name, LocalDate start, LocalDate end) {
        Holiday candidate = new Holiday(id, name, start, end);
        Holiday.requireNoOverlap(candidate, periods.findOverlapping(start, end).stream()
                .map(SettingsService::toHoliday)
                .toList());
        return candidate;
    }

    static Map<String, Object> snapshot(HolidayPeriod p) {
        return Map.of("name", p.getName(),
                "startDate", p.getStartDate().toString(),
                "endDate", p.getEndDate().toString());
    }
}
