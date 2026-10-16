import { longDay, timeOf } from '../shared/dates';

/** `Jueves 12 de marzo, 10:00`: when an appointment is, as the approved mock says it. */
export function dayAndTime(dateTime: string): string {
  const day = longDay(dateTime.slice(0, 10));
  return `${day.charAt(0).toUpperCase()}${day.slice(1)}, ${timeOf(dateTime)}`;
}
