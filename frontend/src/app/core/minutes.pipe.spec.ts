import { describe, expect, it } from 'vitest';
import { CountdownPipe, MinutesPipe } from './minutes.pipe';

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
