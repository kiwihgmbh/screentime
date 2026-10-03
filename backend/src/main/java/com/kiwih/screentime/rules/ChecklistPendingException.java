package com.kiwih.screentime.rules;

import java.util.List;
import java.util.stream.Collectors;

/** Screen time asked for before every item due today was ticked. Maps to 409, with the items. */
public class ChecklistPendingException extends RuleViolation {

    private final List<ChecklistItem> pending;

    public ChecklistPendingException(List<ChecklistItem> pending) {
        super(Kind.CHECKLIST_PENDING, "Tick the checklist first: "
                + pending.stream().map(ChecklistItem::text).collect(Collectors.joining(", ")) + ".");
        this.pending = List.copyOf(pending);
    }

    public List<ChecklistItem> pending() {
        return pending;
    }
}
