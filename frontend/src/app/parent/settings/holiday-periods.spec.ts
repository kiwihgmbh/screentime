import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { DayOfWeek, EffectiveSettings, HolidayPeriod } from '../../core/models';
import { HolidayPeriodsComponent } from './holiday-periods';

describe('HolidayPeriodsComponent', () => {
  let fixture: ComponentFixture<HolidayPeriodsComponent>;
  let http: HttpTestingController;

  const dates = [
    '2026-09-28',
    '2026-09-29',
    '2026-09-30',
    '2026-10-01',
    '2026-10-02',
    '2026-10-03',
    '2026-10-04',
  ];
  const names: DayOfWeek[] = [
    'MONDAY',
    'TUESDAY',
    'WEDNESDAY',
    'THURSDAY',
    'FRIDAY',
    'SATURDAY',
    'SUNDAY',
  ];
  const current: EffectiveSettings = {
    weekStart: dates[0],
    scope: 'TERM',
    holidayDayCount: 0,
    holidayWeekThresholdDays: 4,
    holidayDays: [],
    holidayPeriodNames: [],
    bonusActive: false,
    weeklyBudgetMinutes: 480,
    values: { weeklyMinutes: 480, weekdayCapMinutes: 60, weekendCapMinutes: 120, cutoffHour: 20 },
    days: dates.map((date, i) => ({
      date,
      dayOfWeek: names[i],
      holiday: false,
      capMinutes: i < 5 ? 60 : 120,
    })),
  };
  const autumn: HolidayPeriod = {
    id: 1,
    name: 'Autumn holidays',
    startDate: '2026-10-10',
    endDate: '2026-10-25',
    days: 16,
    createdAt: '2026-09-01T10:00:00Z',
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HolidayPeriodsComponent);
    fixture.componentRef.setInput('current', current);
    fixture.componentRef.setInput('holidayValues', { weeklyMinutes: '720', cutoffHour: '21' });
    http.expectOne((r) => r.url === '/api/holidays').flush([autumn]);
    await fixture.whenStable();
    fixture.detectChanges();
  });

  const root = () => fixture.nativeElement as HTMLElement;

  async function fill(name: string, start: string, end: string): Promise<void> {
    const [nameInput, startInput, endInput] = Array.from(
      root().querySelectorAll('.form input'),
    ) as HTMLInputElement[];
    for (const [input, value] of [
      [nameInput, name],
      [startInput, start],
      [endInput, end],
    ] as const) {
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('lists the periods with their dates and length', () => {
    expect(root().textContent).toContain('Autumn holidays');
    expect(root().textContent).toContain('16 days');
  });

  it('warns when a new period starts inside the current week and names the days', async () => {
    await fill('Autumn holidays early', '2026-10-01', '2026-10-09');
    const warning = root().querySelector('.warning')?.textContent ?? '';
    expect(warning).toContain('Thursday, Friday, Saturday and Sunday become holiday days');
    expect(warning).toContain('switches to holiday values');
  });

  it('does not warn for a period that leaves the current week alone', async () => {
    await fill('Christmas', '2026-12-19', '2027-01-03');
    expect(root().querySelector('.warning')).toBeNull();
  });

  it('refuses a period that ends before it starts before anything is sent', async () => {
    await fill('Backwards', '2026-10-25', '2026-10-10');
    expect(root().textContent).toContain('The last day is before the first day');
    const add = Array.from(root().querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Add'),
    );
    expect(add?.disabled).toBe(true);
  });

  it('shows the server’s answer to an overlap, naming the period in the way', async () => {
    await fill('Camp', '2026-10-25', '2026-10-28');
    (
      Array.from(root().querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Add'),
      ) as HTMLButtonElement
    ).click();

    http.expectOne('/api/holidays').flush(
      {
        status: 409,
        error: 'Conflict',
        message: '2026-10-25 to 2026-10-28 overlaps "Autumn holidays" (2026-10-10 to 2026-10-25).',
      },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root().querySelector('.failure')?.textContent).toContain('Autumn holidays');
  });

  it('asks once more before deleting, without a browser dialog', async () => {
    (
      root().querySelector('button[aria-label="Delete Autumn holidays"]') as HTMLButtonElement
    ).click();
    fixture.detectChanges();
    const confirm = Array.from(root().querySelectorAll('li button')).find(
      (b) => b.textContent?.trim() === 'Delete',
    );
    expect(confirm).toBeTruthy();
    http.expectNone((r) => r.method === 'DELETE');
  });
});
