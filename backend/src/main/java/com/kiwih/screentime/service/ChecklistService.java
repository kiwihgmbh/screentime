package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.ChecklistItemRow;
import com.kiwih.screentime.domain.ChecklistTick;
import com.kiwih.screentime.domain.Session;
import com.kiwih.screentime.domain.SessionType;
import com.kiwih.screentime.repo.ChecklistItemRepository;
import com.kiwih.screentime.repo.ChecklistTickRepository;
import com.kiwih.screentime.rules.Checklist;
import com.kiwih.screentime.rules.ChecklistItem;
import com.kiwih.screentime.rules.RuleViolation;
import com.kiwih.screentime.rules.WeekCalendar;
import com.kiwih.screentime.security.AppPrincipal;
import com.kiwih.screentime.web.dto.ChecklistTickView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The reminders the child ticks before screen time. The parents' list, what is
 * due today by the server's day, the check a start has to pass, and the ticks
 * recorded with it. Every change to an item is audited.
 */
@Service
public class ChecklistService {

    private final ChecklistItemRepository items;
    private final ChecklistTickRepository ticks;
    private final WeekCalendar calendar;
    private final AuditService audit;
    private final Clock clock;

    public ChecklistService(ChecklistItemRepository items, ChecklistTickRepository ticks,
                            WeekCalendar calendar, AuditService audit, Clock clock) {
        this.items = items;
        this.ticks = ticks;
        this.calendar = calendar;
        this.audit = audit;
        this.clock = clock;
    }

    public LocalDate today() {
        return calendar.dayOf(clock.instant());
    }

    /** The items a parent has not removed, in their order. */
    @Transactional(readOnly = true)
    public List<ChecklistItemRow> rows() {
        return items.findByActiveTrueOrderBySortOrderAscIdAsc();
    }

    /** What the child has to tick today, in the parents' order. */
    @Transactional(readOnly = true)
    public List<ChecklistItem> dueToday() {
        return Checklist.dueOn(today(), rows().stream().map(ChecklistService::toRule).toList());
    }

    /**
     * Refuses screen time with an item due today left unticked. Called after
     * the other start rules, so after the cut off the reason is the cut off.
     */
    @Transactional(readOnly = true)
    public List<ChecklistItem> requireTicked(AppPrincipal caller, SessionType type, Collection<Long> tickedIds) {
        if (!Checklist.applies(caller.role(), type)) {
            return List.of();
        }
        List<ChecklistItem> due = dueToday();
        Checklist.requireTicked(caller.role(), type, due, tickedIds);
        return Checklist.ticked(due, tickedIds);
    }

    /** Stores the ticks with the session they unlocked, at the server's time. */
    @Transactional
    public void record(Session session, List<ChecklistItem> ticked, Long userId) {
        Instant at = clock.instant();
        for (ChecklistItem item : ticked) {
            ticks.save(new ChecklistTick(session.getId(), item.id(), item.text(), userId, at));
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ChecklistTickView>> ticksFor(Collection<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        return ticks.findBySessionIdInOrderByIdAsc(sessionIds).stream()
                .collect(Collectors.groupingBy(ChecklistTick::getSessionId, LinkedHashMap::new,
                        Collectors.mapping(t -> new ChecklistTickView(t.getItemId(), t.getItemText(), t.getTickedAt()),
                                Collectors.toList())));
    }

    // ------------------------------------------------------------------ parents

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public ChecklistItemRow create(String text, Set<DayOfWeek> weekdays, LocalDate from, LocalDate until, Long by) {
        ChecklistItem checked = new ChecklistItem(null, text, weekdays, from, until, true);
        int last = rows().stream().mapToInt(ChecklistItemRow::getSortOrder).max().orElse(0);
        ChecklistItemRow saved = items.save(new ChecklistItemRow(checked.text(), checked.weekdays(),
                checked.validFrom(), checked.validUntil(), last + 1, by, clock.instant()));
        audit.created(AuditService.CHECKLIST_ITEM, saved.getId(), snapshot(saved), by);
        return saved;
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public ChecklistItemRow update(Long id, String text, Set<DayOfWeek> weekdays, LocalDate from, LocalDate until,
                                   Long by) {
        ChecklistItemRow row = active(id);
        ChecklistItem checked = new ChecklistItem(id, text, weekdays, from, until, true);
        Map<String, Object> before = snapshot(row);
        row.change(checked.text(), checked.weekdays(), checked.validFrom(), checked.validUntil(), row.getSortOrder());
        audit.updated(AuditService.CHECKLIST_ITEM, id, before, snapshot(row), by);
        return row;
    }

    /** The new order has to name every item exactly once, so nothing silently drops out of it. */
    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public List<ChecklistItemRow> reorder(List<Long> ids, Long by) {
        List<ChecklistItemRow> current = rows();
        Set<Long> known = current.stream().map(ChecklistItemRow::getId).collect(Collectors.toSet());
        if (ids.size() != known.size() || !known.equals(new HashSet<>(ids))) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "The new order has to list every item exactly once: " + known.size() + " items, "
                            + ids.size() + " in the request.");
        }
        Map<Long, ChecklistItemRow> byId = current.stream()
                .collect(Collectors.toMap(ChecklistItemRow::getId, r -> r));
        List<Long> before = current.stream().map(ChecklistItemRow::getId).toList();
        for (int i = 0; i < ids.size(); i++) {
            ChecklistItemRow row = byId.get(ids.get(i));
            row.change(row.getText(), row.getWeekdays(), row.getValidFrom(), row.getValidUntil(), i + 1);
        }
        audit.updated(AuditService.CHECKLIST_ITEM, null, Map.of("order", before), Map.of("order", ids), by);
        return rows();
    }

    /** Switches the item off. The row stays, so what was ticked in the past still reads right. */
    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public void remove(Long id, Long by) {
        ChecklistItemRow row = active(id);
        row.remove();
        audit.deleted(AuditService.CHECKLIST_ITEM, id, snapshot(row), by);
    }

    private ChecklistItemRow active(Long id) {
        return items.findById(id).filter(ChecklistItemRow::isActive)
                .orElseThrow(() -> new NotFoundException("Checklist item", id));
    }

    public static ChecklistItem toRule(ChecklistItemRow row) {
        return new ChecklistItem(row.getId(), row.getText(), row.getWeekdays(),
                row.getValidFrom(), row.getValidUntil(), row.isActive());
    }

    private static Map<String, Object> snapshot(ChecklistItemRow row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", row.getText());
        m.put("weekdays", row.getWeekdays().stream().sorted().map(DayOfWeek::name).toList());
        m.put("validFrom", row.getValidFrom() == null ? null : row.getValidFrom().toString());
        m.put("validUntil", row.getValidUntil() == null ? null : row.getValidUntil().toString());
        return m;
    }
}
