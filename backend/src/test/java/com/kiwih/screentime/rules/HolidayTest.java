package com.kiwih.screentime.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidayTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 10);
    private static final LocalDate END = LocalDate.of(2026, 10, 25);
    private final Holiday autumn = new Holiday(1L, "Autumn holidays", START, END);

    @Test
    void bothEndsAreInside() {
        assertThat(autumn.contains(START)).isTrue();
        assertThat(autumn.contains(END)).isTrue();
        assertThat(autumn.contains(START.minusDays(1))).isFalse();
        assertThat(autumn.contains(END.plusDays(1))).isFalse();
    }

    @Test
    void aPeriodCannotEndBeforeItStarts() {
        assertThatThrownBy(() -> new Holiday(null, "Backwards", END, START))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining(END.toString())
                .hasMessageContaining(START.toString());
    }

    @Test
    void aPeriodNeedsAName() {
        assertThatThrownBy(() -> new Holiday(null, "  ", START, END))
                .isInstanceOf(RuleViolation.class);
    }

    @Test
    void anOverlapIsRefusedAsAConflictNamingTheOtherPeriod() {
        Holiday overlapping = new Holiday(null, "Autumn camp", END, END.plusDays(3));

        assertThatThrownBy(() -> Holiday.requireNoOverlap(overlapping, List.of(autumn)))
                .isInstanceOfSatisfying(HolidayOverlapException.class, e -> {
                    assertThat(e.kind()).isEqualTo(RuleViolation.Kind.CONFLICT);
                    assertThat(e.conflicting()).isEqualTo(autumn);
                    assertThat(e.getMessage()).contains("Autumn holidays")
                            .contains(START.toString()).contains(END.toString());
                });
    }

    @Test
    void aPeriodInsideAnotherIsAnOverlap() {
        Holiday inside = new Holiday(null, "Inside", START.plusDays(2), START.plusDays(3));
        assertThatThrownBy(() -> Holiday.requireNoOverlap(inside, List.of(autumn)))
                .isInstanceOf(HolidayOverlapException.class);
    }

    @Test
    void periodsThatTouchWithoutSharingADayAreFine() {
        Holiday before = new Holiday(null, "Before", START.minusDays(5), START.minusDays(1));
        Holiday after = new Holiday(null, "After", END.plusDays(1), END.plusDays(4));
        Holiday.requireNoOverlap(before, List.of(autumn));
        Holiday.requireNoOverlap(after, List.of(autumn));
    }

    @Test
    void editingAPeriodDoesNotConflictWithItself() {
        Holiday longer = new Holiday(1L, "Autumn holidays", START, END.plusDays(2));
        Holiday.requireNoOverlap(longer, List.of(autumn));
    }
}
