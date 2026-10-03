package com.kiwih.screentime.rules;

import com.kiwih.screentime.domain.Role;
import com.kiwih.screentime.domain.SessionType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The reminders the child ticks before screen time starts.
 *
 * Every start of screen time, by timer or by hand, needs every item due that
 * day ticked, every time: a list that disappears once ticked is a list that is
 * clicked away and forgotten. The ticks travel with the start request, so the
 * server decides against its own day and its own list, never the browser's.
 *
 * Looking things up is not blocked, because it is often the homework itself.
 * A parent is not blocked either; the checklist is the child's.
 */
public final class Checklist {

    private Checklist() {
    }

    /** The items due on a local day, in the order the parents put them. */
    public static List<ChecklistItem> dueOn(LocalDate day, List<ChecklistItem> items) {
        return items.stream().filter(i -> i.dueOn(day)).toList();
    }

    /** Whether this kind of start needs the checklist at all. */
    public static boolean applies(Role role, SessionType type) {
        return role == Role.CHILD && type == SessionType.FUN;
    }

    /**
     * Refuses the start when a due item is not ticked, naming what is left. A
     * tick for an item that is not due is ignored: the list in the browser
     * can be a moment older than the server's.
     */
    public static void requireTicked(Role role, SessionType type, List<ChecklistItem> due,
                                     Collection<Long> tickedIds) {
        if (!applies(role, type)) {
            return;
        }
        Set<Long> ticked = new HashSet<>(tickedIds == null ? List.of() : tickedIds);
        List<ChecklistItem> pending = due.stream().filter(i -> !ticked.contains(i.id())).toList();
        if (!pending.isEmpty()) {
            throw new ChecklistPendingException(pending);
        }
    }

    /** The due items the child ticked, in the parents' order: what gets recorded with the start. */
    public static List<ChecklistItem> ticked(List<ChecklistItem> due, Collection<Long> tickedIds) {
        Set<Long> ticked = new HashSet<>(tickedIds);
        return due.stream().filter(i -> ticked.contains(i.id())).toList();
    }
}
