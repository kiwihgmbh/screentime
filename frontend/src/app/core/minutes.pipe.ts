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

/**
 * Seconds of use the same way: "1 h 12 min 30 s", "40 s", "8 h".
 *
 * Time is counted to the second, so a 40 second session shows as 40 s and not
 * as a "0 min" that still made the balance move. Zero parts are left out,
 * except the minutes between hours and seconds, so "1 h 0 min 5 s" cannot be
 * read as an hour and five minutes.
 *
 * 'compact' is for the big numbers on the dashboard: from an hour up the
 * seconds are left out, rounded down, because "7 h 59 min 57 s" wraps onto a
 * second line and the seconds only matter once less than an hour is left.
 */
@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(value: number | null | undefined, mode: 'exact' | 'compact' = 'exact'): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '–';
    }
    const negative = value < 0;
    let total = Math.abs(Math.trunc(value));
    if (mode === 'compact' && total >= 3600) {
      total -= total % 60;
    }
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const seconds = total % 60;

    const parts: string[] = [];
    if (hours > 0) {
      parts.push(`${hours} h`);
    }
    if (minutes > 0 || (hours > 0 && seconds > 0)) {
      parts.push(`${minutes} min`);
    }
    if (seconds > 0) {
      parts.push(`${seconds} s`);
    }
    const text = parts.length === 0 ? '0 min' : parts.join(' ');
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
