import { describe, expect, it } from 'vitest';
import { checklistItemProblem, describeSchedule as describeIn } from './checklist-text';

describe('describeSchedule', () => {
  // the year is fixed, or these would break on the first of January
  const describeSchedule = (item: Parameters<typeof describeIn>[0], year = 2026): string =>
    describeIn(item, year);

  it('says every day when nothing limits it', () => {
    expect(describeSchedule({ weekdays: [] })).toBe('Every day');
  });

  it('names the weekdays, in week order', () => {
    expect(describeSchedule({ weekdays: ['SATURDAY'] })).toBe('Saturdays');
    expect(describeSchedule({ weekdays: ['FRIDAY', 'MONDAY', 'WEDNESDAY'] })).toBe(
      'Mondays, Wednesdays and Fridays',
    );
  });

  it('calls Monday to Friday school days and the other two the weekend', () => {
    expect(
      describeSchedule({ weekdays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] }),
    ).toBe('School days');
    expect(describeSchedule({ weekdays: ['SUNDAY', 'SATURDAY'] })).toBe('Weekends');
    expect(
      describeSchedule({
        weekdays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
      }),
    ).toBe('Every day');
  });

  it('adds the dates', () => {
    expect(
      describeSchedule({ weekdays: [], validFrom: '2026-10-05', validUntil: '2026-10-09' }),
    ).toBe('Every day, 5 Oct to 9 Oct');
    expect(describeSchedule({ weekdays: ['SATURDAY'], validUntil: '2026-12-19' })).toBe(
      'Saturdays, until 19 Dec',
    );
    expect(describeSchedule({ weekdays: [], validFrom: '2026-10-05' })).toBe(
      'Every day, from 5 Oct',
    );
  });

  it('says a single day as one date', () => {
    expect(
      describeSchedule({ weekdays: [], validFrom: '2026-10-09', validUntil: '2026-10-09' }),
    ).toBe('Only on 9 Oct');
  });

  it('shows the year when the dates are not this year', () => {
    expect(
      describeSchedule({ weekdays: [], validFrom: '2027-01-04', validUntil: '2027-01-08' }, 2026),
    ).toBe('Every day, 4 Jan 2027 to 8 Jan 2027');
  });
});

describe('checklistItemProblem', () => {
  it('needs a text', () => {
    expect(checklistItemProblem({ text: '  ', weekdays: [] })).toBe('Write what to remember.');
  });

  it('keeps the text short', () => {
    expect(checklistItemProblem({ text: 'x'.repeat(200), weekdays: [] })).toBeNull();
    expect(checklistItemProblem({ text: 'x'.repeat(201), weekdays: [] })).toContain('201');
  });

  it('does not let the last day come before the first', () => {
    expect(
      checklistItemProblem({
        text: 'Homework',
        weekdays: [],
        validFrom: '2026-10-09',
        validUntil: '2026-10-05',
      }),
    ).toBe('The last day is before the first day.');
  });

  it('accepts a single day and open ends', () => {
    expect(
      checklistItemProblem({
        text: 'Homework',
        weekdays: [],
        validFrom: '2026-10-09',
        validUntil: '2026-10-09',
      }),
    ).toBeNull();
    expect(
      checklistItemProblem({ text: 'Homework', weekdays: [], validFrom: '2026-10-09' }),
    ).toBeNull();
  });
});
