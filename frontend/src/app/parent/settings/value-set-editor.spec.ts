import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { Settings } from '../../core/models';
import { VALUE_KEYS } from '../../core/settings-rules';
import { ValueSetEditorComponent } from './value-set-editor';

/**
 * A parent sees what a field has to be, sees at once when it is not, and
 * cannot save a set that does not hold together.
 */
describe('ValueSetEditorComponent', () => {
  let fixture: ComponentFixture<ValueSetEditorComponent>;
  let http: HttpTestingController;

  const TERM: Settings = {
    weeklyMinutes: '480',
    weekdayCapMinutes: '60',
    weekendCapMinutes: '120',
    quickDailyMinutes: '15',
    cutoffHour: '20',
    bonusMinutes: '60',
    bonusWeekendCapMinutes: '150',
    maxPenaltyMinutes: '120',
    toleranceMinutes: '10',
    manualMaxMinutes: '240',
    deliberatePenaltyMinutes: '60',
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ValueSetEditorComponent);
    fixture.componentRef.setInput('scope', 'TERM');
    fixture.componentRef.setInput('title', 'term values');
    fixture.componentRef.setInput('keys', VALUE_KEYS);
    fixture.componentRef.setInput('thisWeek', TERM);
    fixture.componentRef.setInput('nextWeek', { ...TERM, weeklyMinutes: '420' });
    fixture.componentRef.setInput('thisMonday', '2026-09-28');
    fixture.componentRef.setInput('nextMonday', '2026-10-05');
    fixture.detectChanges();
    await fixture.whenStable();
  });

  const root = () => fixture.nativeElement as HTMLElement;
  const field = (key: string) => root().querySelector(`.field[data-key="${key}"]`) as HTMLElement;
  const saveButton = () =>
    Array.from(root().querySelectorAll('button')).find((b) =>
      b.textContent?.includes('ave'),
    ) as HTMLButtonElement;

  async function type(key: string, value: string): Promise<void> {
    const input = field(key).querySelector('input') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('shows each value with its rule underneath', () => {
    const weekly = field('weeklyMinutes');
    expect((weekly.querySelector('input') as HTMLInputElement).value).toBe('480');
    expect(weekly.querySelector('.rule')?.textContent).toContain(
      '5 × school day + 2 × weekend ceiling',
    );
    expect(field('cutoffHour').querySelector('.rule')?.textContent).toContain('12 to 23');
  });

  it('says when a value is already planned for next week', () => {
    expect(field('weeklyMinutes').querySelector('.pending')?.textContent).toContain('420');
    expect(field('weekdayCapMinutes').querySelector('.pending')).toBeNull();
  });

  it('has nothing to save until something changes', () => {
    expect(saveButton().disabled).toBe(true);
  });

  it('marks every field a broken rule concerns, names the numbers, and disables save', async () => {
    await type('weekendCapMinutes', '30');

    for (const key of ['weeklyMinutes', 'weekdayCapMinutes', 'weekendCapMinutes']) {
      const error = field(key).querySelector('.error')?.textContent ?? '';
      expect(error, key).toContain('360');
      expect(error, key).toContain('480');
    }
    expect(field('cutoffHour').querySelector('.error')).toBeNull();
    expect(saveButton().disabled).toBe(true);
    expect(saveButton().textContent).toContain('Fix the marked fields');
  });

  it('clears the message once the rule holds again', async () => {
    await type('cutoffHour', '9');
    expect(field('cutoffHour').querySelector('.error')?.textContent).toContain('9');
    await type('cutoffHour', '21');
    expect(field('cutoffHour').querySelector('.error')).toBeNull();
    expect(saveButton().disabled).toBe(false);
  });

  it('saves only what changed, from this Monday by default', async () => {
    await type('toleranceMinutes', '15');
    saveButton().click();

    const request = http.expectOne('/api/settings');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      scope: 'TERM',
      values: { toleranceMinutes: '15' },
      validFrom: '2026-09-28',
    });
  });

  it('saves from next Monday when that is chosen, compared with next week’s values', async () => {
    const next = root().querySelectorAll('mat-radio-button input')[1] as HTMLInputElement;
    next.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect((field('weeklyMinutes').querySelector('input') as HTMLInputElement).value).toBe('420');
    await type('toleranceMinutes', '15');
    saveButton().click();

    const request = http.expectOne('/api/settings');
    expect(request.request.body.validFrom).toBe('2026-10-05');
  });

  it('says plainly which weeks each choice touches', () => {
    const text = root().querySelector('mat-radio-group')?.textContent ?? '';
    expect(text).toContain('From this week');
    expect(text).toContain('Last week and checks already made stay as they were');
    expect(text).toContain('From next week');
    expect(text).toContain('This week stays as it is');
  });

  it('shows what the server refused under the fields it names', async () => {
    await type('weekdayCapMinutes', '55');
    saveButton().click();

    http.expectOne('/api/settings').flush(
      {
        status: 400,
        error: 'Bad Request',
        message: 'From the week of 2026-10-05, term values: 515 is not more than 520.',
        details: {
          problems: [
            {
              rule: 'DAILY_CEILINGS_EXCEED_WEEK',
              keys: ['weeklyMinutes', 'weekdayCapMinutes', 'weekendCapMinutes'],
              message: 'From the week of 2026-10-05, term values: 515 is not more than 520.',
            },
          ],
        },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(field('weekdayCapMinutes').querySelector('.error')?.textContent).toContain('2026-10-05');
  });
});
