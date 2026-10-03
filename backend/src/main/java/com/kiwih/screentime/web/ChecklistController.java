package com.kiwih.screentime.web;

import com.kiwih.screentime.domain.ChecklistItemRow;
import com.kiwih.screentime.security.CurrentUser;
import com.kiwih.screentime.service.AccountService;
import com.kiwih.screentime.service.ChecklistService;
import com.kiwih.screentime.web.dto.ChecklistItemRequest;
import com.kiwih.screentime.web.dto.ChecklistItemView;
import com.kiwih.screentime.web.dto.ChecklistOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The parents' side of the checklist. The child's side is on the dashboard and the start. */
@RestController
@RequestMapping("/api/checklist")
public class ChecklistController {

    private final ChecklistService checklist;
    private final AccountService accounts;
    private final CurrentUser currentUser;

    public ChecklistController(ChecklistService checklist, AccountService accounts, CurrentUser currentUser) {
        this.checklist = checklist;
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Every item, in the order the child sees them")
    @GetMapping
    public List<ChecklistItemView> list() {
        return views(checklist.rows());
    }

    @Operation(summary = "Add an item at the end of the list")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChecklistItemView create(@Valid @RequestBody ChecklistItemRequest r) {
        return view(checklist.create(r.text(), r.weekdays(), r.validFrom(), r.validUntil(),
                currentUser.require().userId()), checklist.today(), accounts.userNames());
    }

    @Operation(summary = "Put the items in a new order; every item has to be listed once")
    @PutMapping("/order")
    public List<ChecklistItemView> reorder(@Valid @RequestBody ChecklistOrderRequest r) {
        return views(checklist.reorder(r.ids(), currentUser.require().userId()));
    }

    @Operation(summary = "Change an item. What was ticked before keeps the old text.")
    @PutMapping("/{id}")
    public ChecklistItemView update(@PathVariable Long id, @Valid @RequestBody ChecklistItemRequest r) {
        return view(checklist.update(id, r.text(), r.weekdays(), r.validFrom(), r.validUntil(),
                currentUser.require().userId()), checklist.today(), accounts.userNames());
    }

    @Operation(summary = "Remove an item. Past ticks of it stay readable.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        checklist.remove(id, currentUser.require().userId());
    }

    private List<ChecklistItemView> views(List<ChecklistItemRow> rows) {
        LocalDate today = checklist.today();
        Map<Long, String> names = accounts.userNames();
        return rows.stream().map(r -> view(r, today, names)).toList();
    }

    private static ChecklistItemView view(ChecklistItemRow r, LocalDate today, Map<Long, String> names) {
        return new ChecklistItemView(r.getId(), r.getText(), r.getWeekdays().stream().sorted().toList(),
                r.getValidFrom(), r.getValidUntil(), r.getSortOrder(),
                ChecklistService.toRule(r).dueOn(today), names.get(r.getCreatedBy()), r.getCreatedAt());
    }
}
