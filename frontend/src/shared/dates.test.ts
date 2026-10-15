import { describe, expect, it } from 'vitest';

import { addDays, longDay, nowAtThePractice, timeOf, todayAtThePractice } from './dates';

describe("the practice's days and times", () => {
  it('reads a time as the API sends it', () => {
    expect(timeOf('2026-03-10T09:05:00')).toBe('09:05');
  });

  it('moves a day across months and years', () => {
    expect(addDays('2026-03-10', 1)).toBe('2026-03-11');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('names a day in Spanish whatever the browser’s zone', () => {
    expect(longDay('2026-03-10')).toBe('martes 10 de marzo');
  });

  it("tells the time at the practice, not the browser's", () => {
    // 13:42 UTC is 10:42 in Buenos Aires.
    expect(nowAtThePractice(new Date('2026-10-13T13:42:00Z'))).toBe('martes 13 de octubre · 10:42');
  });

  it("tells today at the practice, not the browser's", () => {
    // 02:30 UTC of the 11th is still the 10th in Buenos Aires, and already the 11th in Kiritimati.
    expect(todayAtThePractice(new Date('2026-03-11T02:30:00Z'))).toBe('2026-03-10');
    expect(todayAtThePractice(new Date('2026-03-11T03:30:00Z'))).toBe('2026-03-11');
  });
});
