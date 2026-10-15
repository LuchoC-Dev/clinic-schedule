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

const WEEKDAYS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];

/** `10/03/2026`. */
export function shortDay(day: string): string {
  const [year, month, date] = day.slice(0, 10).split('-');
  return `${date}/${month}/${year}`;
}

/** `mar 10/03/2026`, the day of a time as the API sends it. */
export function weekdayDay(dateTime: string): string {
  const [year, month, date] = dateTime.slice(0, 10).split('-').map(Number);
  return `${WEEKDAYS[new Date(Date.UTC(year, month - 1, date)).getUTCDay()]} ${shortDay(dateTime)}`;
}

/** `mar 10/03/2026 09:30`: when something was done, as the histories say it. */
export function when(dateTime: string): string {
  return `${weekdayDay(dateTime)} ${timeOf(dateTime)}`;
}

/** Today at the practice, as `2026-03-10`: what a patient's age is counted to, never a rule. */
export function todayAtThePractice(now: Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    year: 'numeric', month: '2-digit', day: '2-digit', timeZone: PRACTICE_TIME_ZONE,
  }).formatToParts(now);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((each) => each.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

/** Now at the practice, as `2026-03-10T09:30`: to compare with the times the API sends, never for a rule. */
export function momentAtThePractice(now: Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
    timeZone: PRACTICE_TIME_ZONE,
  }).formatToParts(now);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((each) => each.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}`;
}
