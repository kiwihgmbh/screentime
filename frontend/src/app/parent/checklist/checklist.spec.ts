import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ChecklistItem } from '../../core/models';
import { ChecklistComponent } from './checklist';

describe('ChecklistComponent', () => {
  let fixture: ComponentFixture<ChecklistComponent>;
  let http: HttpTestingController;

  const items: ChecklistItem[] = [
    {
      id: 1,
      text: 'Homework',
      weekdays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      sortOrder: 1,
      dueToday: true,
      createdAt: '2026-10-01T10:00:00Z',
    },
    {
      id: 2,
      text: 'Laundry',
      weekdays: ['SATURDAY'],
      sortOrder: 2,
      dueToday: false,
      createdAt: '2026-10-01T10:00:00Z',
    },
  ];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChecklistComponent);
    http.expectOne('/api/checklist').flush(items);
    await fixture.whenStable();
    fixture.detectChanges();
  });

  const root = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) =>
    Array.from(root().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    ) as HTMLButtonElement;

  async function type(name: string, value: string): Promise<void> {
    const input = root().querySelector(`input[name="${name}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('lists the items in order, with when they are due', () => {
    const rows = Array.from(root().querySelectorAll('li')).map((li) => li.textContent ?? '');
    expect(rows[0]).toContain('Homework');
    expect(rows[0]).toContain('School days');
    expect(rows[0]).toContain('TODAY');
    expect(rows[1]).toContain('Saturdays');
    expect(rows[1]).not.toContain('TODAY');
  });

  it('cannot move the first item up or the last one down', () => {
    expect(
      (root().querySelector('button[aria-label="Move Homework up"]') as HTMLButtonElement).disabled,
    ).toBe(true);
    expect(
      (root().querySelector('button[aria-label="Move Laundry down"]') as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });

  it('moves an item by sending the whole new order', () => {
    (root().querySelector('button[aria-label="Move Laundry up"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/checklist/order');
    expect(request.request.body).toEqual({ ids: [2, 1] });
  });

  it('says in words when a new item will be due, and adds it', async () => {
    expect(button('addAdd').disabled).toBe(true);
    await type('checklist-text', 'Pack the swimming bag');
    await type('checklist-from', '2026-10-09');
    await type('checklist-until', '2026-10-09');

    expect(root().querySelector('[data-testid="preview"]')?.textContent).toContain('Only on 9 Oct');
    button('addAdd').click();

    const request = http.expectOne('/api/checklist');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      text: 'Pack the swimming bag',
      weekdays: [],
      validFrom: '2026-10-09',
      validUntil: '2026-10-09',
    });
  });

  it('refuses a last day before the first before anything is sent', async () => {
    await type('checklist-text', 'Backwards');
    await type('checklist-from', '2026-10-09');
    await type('checklist-until', '2026-10-05');
    expect(root().textContent).toContain('The last day is before the first day');
    expect(button('addAdd').disabled).toBe(true);
  });

  it('asks once more before deleting, on the page and not in a dialog', () => {
    (root().querySelector('button[aria-label="Delete Homework"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(button('Delete')).toBeTruthy();
    http.expectNone((r) => r.method === 'DELETE');
  });
});
