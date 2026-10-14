/**
 * Days and times as the API sends them: in the practice's time zone, without one (D-10) — a day as
 * `2026-03-10`, a time as `2026-03-10T09:30:00`. They are read as text, never through the browser's clock or
 * zone, so a browser elsewhere still shows the practice's hours.
 */

export const PRACTICE_TIME_ZONE = 'America/Argentina/Buenos_Aires';

/** `09:30` of `2026-03-10T09:30:00`. */
export function timeOf(dateTime: string): string {
  return dateTime.slice(11, 16);
}

/** The day so many days after (or before, negative) another, both as `2026-03-10`. */
export function addDays(day: string, days: number): string {
  const [year, month, date] = day.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, date + days)).toISOString().slice(0, 10);
}

/** `martes 10 de marzo`. */
export function longDay(day: string): string {
  const [year, month, date] = day.split('-').map(Number);
  return new Intl.DateTimeFormat('es-AR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' })
    .format(new Date(Date.UTC(year, month - 1, date)))
    .replace(',', '');
}

/** `martes 13 de octubre · 10:42`, now at the practice: for the header, never for a rule. */
export function nowAtThePractice(now: Date): string {
  const parts = new Intl.DateTimeFormat('es-AR', {
    weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
    timeZone: PRACTICE_TIME_ZONE,
  }).formatToParts(now);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((each) => each.type === type)?.value ?? '';
  return `${part('weekday')} ${part('day')} de ${part('month')} · ${part('hour')}:${part('minute')}`;
}
