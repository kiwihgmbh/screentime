import { Pipe, PipeTransform } from '@angular/core';

/**
 * Minutes as a person would say them: "1 h 35 min", "45 min", "2 h".
 *
 * A child reads these numbers several times a day, and "95 min" is harder to
 * judge against "an hour and a half" than it looks.
 */
@Pipe({ name: 'minutes' })
export class MinutesPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '–';
    }
    const negative = value < 0;
    const total = Math.abs(Math.trunc(value));
    const hours = Math.floor(total / 60);
    const minutes = total % 60;

    let text: string;
    if (hours === 0) {
      text = `${minutes} min`;
    } else if (minutes === 0) {
      text = `${hours} h`;
    } else {
      text = `${hours} h ${minutes} min`;
    }
    return negative ? `− ${text}` : text;
  }
}

/** The same number as a clock, for a countdown: "0:09:12". */
@Pipe({ name: 'countdown' })
export class CountdownPipe implements PipeTransform {
  transform(seconds: number | null | undefined): string {
    if (seconds === null || seconds === undefined || Number.isNaN(seconds)) {
      return '–';
    }
    const over = seconds < 0;
    const total = Math.abs(Math.trunc(seconds));
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    const text = `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return over ? `− ${text}` : text;
  }
}
