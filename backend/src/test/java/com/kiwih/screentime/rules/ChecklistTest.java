package com.kiwih.screentime.rules;

import com.kiwih.screentime.domain.Role;
import com.kiwih.screentime.domain.SessionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static java.time.DayOfWeek.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which reminders are due on a day, and what a start needs before it is
 * allowed. The child ticks every item due today before screen time starts,
 * every time, and the ticks are sent with the start itself.
 */
class ChecklistTest {

    // Monday 5 October 2026 to Sunday 11 October 2026
    private static final LocalDate MON = LocalDate.of(2026, 10, 5);
    private static final LocalDate FRI = MON.plusDays(4);
    private static final LocalDate SAT = MON.plusDays(5);

    @Nested
    @DisplayName("when an item is due")
    class Due {

        @Test
        void anItemWithoutDatesOrDaysIsAlwaysDue() {
            ChecklistItem always = item(1, "Homework first", Set.of(), null, null);
            for (int i = 0; i < 7; i++) {
                assertThat(always.dueOn(MON.plusDays(i))).isTrue();
            }
        }

        @Test
        void weekdaysLimitItToThoseDays() {
            ChecklistItem laundry = item(1, "Laundry", Set.of(SATURDAY), null, null);
            assertThat(laundry.dueOn(SAT)).isTrue();
            assertThat(laundry.dueOn(FRI)).isFalse();
        }

        @Test
        void aDateRangeIncludesBothEnds() {
            ChecklistItem week = item(1, "Practise for the test", Set.of(), MON, FRI);
            assertThat(week.dueOn(MON)).isTrue();
            assertThat(week.dueOn(FRI)).isTrue();
            assertThat(week.dueOn(MON.minusDays(1))).isFalse();
            assertThat(week.dueOn(SAT)).isFalse();
        }

        @Test
        void aSingleDayIsARangeOfOneDay() {
            ChecklistItem once = item(1, "Pack the swimming bag", Set.of(), FRI, FRI);
            assertThat(once.dueOn(FRI)).isTrue();
            assertThat(once.dueOn(FRI.minusDays(1))).isFalse();
            assertThat(once.dueOn(SAT)).isFalse();
        }

        @Test
        void onlyAStartOrOnlyAnEndIsOpenOnTheOtherSide() {
            assertThat(item(1, "From Friday on", Set.of(), FRI, null).dueOn(FRI.plusYears(1))).isTrue();
            assertThat(item(1, "From Friday on", Set.of(), FRI, null).dueOn(MON)).isFalse();
            assertThat(item(1, "Until Friday", Set.of(), null, FRI).dueOn(MON.minusYears(1))).isTrue();
            assertThat(item(1, "Until Friday", Set.of(), null, FRI).dueOn(SAT)).isFalse();
        }

        @Test
        void weekdaysAndARangeTogetherNeedBoth() {
            // homework on school days, but only until the holidays start
            ChecklistItem homework = item(1, "Homework", Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
                    null, FRI);
            assertThat(homework.dueOn(FRI)).isTrue();
            assertThat(homework.dueOn(FRI.plusDays(3))).as("Monday after the range").isFalse();
            assertThat(homework.dueOn(SAT)).as("in the range, wrong day").isFalse();
        }

        @Test
        void aRemovedItemIsNeverDue() {
            ChecklistItem removed = new ChecklistItem(1L, "Old reminder", Set.of(), null, null, false);
            assertThat(removed.dueOn(MON)).isFalse();
        }

        @Test
        void theDayIsTheLocalDayNotTheUtcOne() {
            // 00:05 on Saturday in Zurich is still Friday 22:05 UTC. An item
            // that ended on Friday must not be due any more.
            WeekCalendar calendar = new WeekCalendar(ZoneId.of("Europe/Zurich"));
            Instant justAfterMidnight = SAT.atTime(0, 5).atZone(ZoneId.of("Europe/Zurich")).toInstant();
            ChecklistItem untilFriday = item(1, "Until Friday", Set.of(), null, FRI);

            assertThat(Checklist.dueOn(calendar.dayOf(justAfterMidnight), List.of(untilFriday))).isEmpty();
        }

        @Test
        void dueItemsKeepTheParentsOrder() {
            List<ChecklistItem> items = List.of(
                    item(3, "Homework", Set.of(), null, null),
                    item(1, "Laundry", Set.of(SATURDAY), null, null),
                    item(2, "Tidy the room", Set.of(), null, null));
            assertThat(Checklist.dueOn(FRI, items)).extracting(ChecklistItem::text)
                    .containsExactly("Homework", "Tidy the room");
        }
    }

    @Nested
    @DisplayName("what a start needs")
    class Starting {

        private final List<ChecklistItem> due = List.of(
                item(1, "Homework", Set.of(), null, null),
                item(2, "Laundry", Set.of(), null, null));

        @Test
        void screenTimeNeedsEveryDueItemTicked() {
            Checklist.requireTicked(Role.CHILD, SessionType.FUN, due, List.of(1L, 2L));
        }

        @Test
        void aMissingTickIsRefusedAndNamesWhatIsLeft() {
            assertThatThrownBy(() -> Checklist.requireTicked(Role.CHILD, SessionType.FUN, due, List.of(1L)))
                    .isInstanceOfSatisfying(ChecklistPendingException.class, e -> {
                        assertThat(e.kind()).isEqualTo(RuleViolation.Kind.CHECKLIST_PENDING);
                        assertThat(e.pending()).extracting(ChecklistItem::text).containsExactly("Laundry");
                        assertThat(e.getMessage()).contains("Laundry");
                    });
        }

        @Test
        void noTicksAtAllIsRefused() {
            assertThatThrownBy(() -> Checklist.requireTicked(Role.CHILD, SessionType.FUN, due, List.of()))
                    .isInstanceOfSatisfying(ChecklistPendingException.class,
                            e -> assertThat(e.pending()).hasSize(2));
        }

        @Test
        void aTickForSomethingNotDueIsIgnored() {
            // the list in the browser can be a little older than the server's
            Checklist.requireTicked(Role.CHILD, SessionType.FUN, due, List.of(1L, 2L, 99L));
        }

        @Test
        void nothingDueMeansNothingToTick() {
            Checklist.requireTicked(Role.CHILD, SessionType.FUN, List.of(), List.of());
        }

        @Test
        void lookingThingsUpIsNotBlocked() {
            // looking something up is often the homework itself
            Checklist.requireTicked(Role.CHILD, SessionType.QUICK, due, List.of());
        }

        @Test
        void aParentIsNotBlocked() {
            Checklist.requireTicked(Role.PARENT, SessionType.FUN, due, List.of());
            Checklist.requireTicked(Role.PARENT, SessionType.FILM, due, List.of());
        }

        @Test
        void theTicksThatCountAreTheDueOnesInOrder() {
            assertThat(Checklist.ticked(due, List.of(2L, 99L, 1L)))
                    .extracting(ChecklistItem::text).containsExactly("Homework", "Laundry");
        }
    }

    @Nested
    @DisplayName("what an item has to be")
    class Valid {

        @Test
        void itNeedsText() {
            assertThatThrownBy(() -> item(1, "  ", Set.of(), null, null))
                    .isInstanceOf(RuleViolation.class);
        }

        @Test
        void theTextIsShort() {
            item(1, "x".repeat(200), Set.of(), null, null);
            assertThatThrownBy(() -> item(1, "x".repeat(201), Set.of(), null, null))
                    .isInstanceOf(RuleViolation.class)
                    .hasMessageContaining("200");
        }

        @Test
        void theRangeCannotEndBeforeItStarts() {
            assertThatThrownBy(() -> item(1, "Backwards", Set.of(), FRI, MON))
                    .isInstanceOf(RuleViolation.class)
                    .hasMessageContaining(FRI.toString())
                    .hasMessageContaining(MON.toString());
        }

        @Test
        void theTextIsTrimmed() {
            assertThat(item(1, "  Homework  ", Set.of(), null, null).text()).isEqualTo("Homework");
        }

        @Test
        void noWeekdaysMeansEveryDay() {
            assertThat(item(1, "Homework", null, null, null).weekdays()).isEmpty();
        }
    }

    private static ChecklistItem item(long id, String text, Set<DayOfWeek> days, LocalDate from, LocalDate until) {
        return new ChecklistItem(id, text, days, from, until, true);
    }
}
