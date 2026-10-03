import { describe, expect, it } from 'vitest';
import { CountdownPipe, DurationPipe, MinutesPipe } from './minutes.pipe';

describe('MinutesPipe', () => {
  const pipe = new MinutesPipe();

  it('says minutes under an hour as minutes', () => {
    expect(pipe.transform(0)).toBe('0 min');
    expect(pipe.transform(45)).toBe('45 min');
  });

  it('says whole hours without a stray zero', () => {
    expect(pipe.transform(60)).toBe('1 h');
    expect(pipe.transform(480)).toBe('8 h');
  });

  it('says the awkward numbers the way a person would', () => {
    // a child has to judge "95" against "an hour and a half" several times a day
    expect(pipe.transform(95)).toBe('1 h 35 min');
    expect(pipe.transform(540)).toBe('9 h');
    expect(pipe.transform(541)).toBe('9 h 1 min');
  });

  it('marks a deduction as negative rather than dropping the sign', () => {
    expect(pipe.transform(-60)).toBe('− 1 h');
    expect(pipe.transform(-30)).toBe('− 30 min');
  });

  it('shows a dash rather than NaN when there is no number', () => {
    expect(pipe.transform(null)).toBe('–');
    expect(pipe.transform(undefined)).toBe('–');
    expect(pipe.transform(Number.NaN)).toBe('–');
  });
});

describe('DurationPipe', () => {
  const pipe = new DurationPipe();

  it('shows seconds, because a session is counted to the second', () => {
    expect(pipe.transform(40)).toBe('40 s');
    expect(pipe.transform(90)).toBe('1 min 30 s');
    expect(pipe.transform(4530)).toBe('1 h 15 min 30 s');
  });

  it('leaves the seconds out when there are none', () => {
    expect(pipe.transform(0)).toBe('0 min');
    expect(pipe.transform(45 * 60)).toBe('45 min');
    expect(pipe.transform(8 * 3600)).toBe('8 h');
    expect(pipe.transform(95 * 60)).toBe('1 h 35 min');
  });

  it('keeps the minutes between hours and seconds so 5 s is not read as 5 min', () => {
    expect(pipe.transform(3605)).toBe('1 h 0 min 5 s');
  });

  it('drops a part second rather than rounding it up', () => {
    expect(pipe.transform(59.9)).toBe('59 s');
  });

  it('marks a deduction as negative rather than dropping the sign', () => {
    expect(pipe.transform(-30 * 60)).toBe('− 30 min');
    expect(pipe.transform(-45)).toBe('− 45 s');
  });

  it('shows a dash rather than NaN when there is no number', () => {
    expect(pipe.transform(null)).toBe('–');
    expect(pipe.transform(undefined)).toBe('–');
    expect(pipe.transform(Number.NaN)).toBe('–');
  });

  it('in compact form drops the seconds once there is an hour or more', () => {
    // "7 h 59 min 57 s" wraps the headline onto two lines and says nothing
    // the child acts on; under an hour the seconds matter again
    expect(pipe.transform(7 * 3600 + 59 * 60 + 57, 'compact')).toBe('7 h 59 min');
    expect(pipe.transform(3600 + 5, 'compact')).toBe('1 h');
    expect(pipe.transform(59 * 60 + 59, 'compact')).toBe('59 min 59 s');
    expect(pipe.transform(40, 'compact')).toBe('40 s');
    expect(pipe.transform(-(2 * 3600 + 30), 'compact')).toBe('− 2 h');
  });
});

describe('CountdownPipe', () => {
  const pipe = new CountdownPipe();

  it('reads as a clock', () => {
    expect(pipe.transform(0)).toBe('0:00:00');
    expect(pipe.transform(59)).toBe('0:00:59');
    expect(pipe.transform(60)).toBe('0:01:00');
    expect(pipe.transform(3600)).toBe('1:00:00');
    expect(pipe.transform(3661)).toBe('1:01:01');
  });

  it('keeps counting once the budget is gone, and says so', () => {
    // the app counts, it does not enforce: going over is shown, not hidden
    expect(pipe.transform(-75)).toBe('− 0:01:15');
  });
});
