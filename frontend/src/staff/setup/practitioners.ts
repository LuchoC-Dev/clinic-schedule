import { timeOf, weekdayDay } from '../../shared/dates';
import type { ApiError } from '../../shared/api';
import { byCode } from '../../shared/refusals';
import type { Refusals } from '../../shared/errors';

/** A practitioner with everything reception set up for them (D-28, D-46, D-82). */
export interface PractitionerSetup {
  id: string;
  fullName: string;
  reasons: { id: string; name: string; durationMinutes: number; webBookable: boolean }[];
  weeklySchedule: Stretch[];
  timeOff: Absence[];
}

export type Day = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface Stretch {
  day: Day;
  startsAt: string;
  endsAt: string;
}

export interface Absence {
  startsAt: string;
  endsAt: string;
}

/** A row of the week as it is typed: any of it may be empty. */
export interface WeekRow {
  day: Day | '';
  startsAt: string;
  endsAt: string;
}

/** What an absence did to the agenda (D-46): who was cancelled, and who arrived already and is reception's. */
export interface TimeOffResult {
  cancelled: { id: string; patientId: string; startsAt: string }[];
  needsManualHandling: { id: string; patientId: string; startsAt: string }[];
  /** The names of the patients of both, by id. */
  patients: Record<string, string>;
}

export const DAYS: { value: Day; label: string }[] = [
  { value: 'MONDAY', label: 'Lunes' },
  { value: 'TUESDAY', label: 'Martes' },
  { value: 'WEDNESDAY', label: 'Miércoles' },
  { value: 'THURSDAY', label: 'Jueves' },
  { value: 'FRIDAY', label: 'Viernes' },
  { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' },
];

/** Empty rows offered below the week's stretches, to add new ones, as the Thymeleaf screen does. */
export const EMPTY_ROWS = 3;

/** The week as rows to edit: each stretch, with its times as `09:00`, and a few empty ones. */
export function weekRows(schedule: Stretch[]): WeekRow[] {
  return [
    ...schedule.map((stretch) => ({ day: stretch.day, startsAt: stretch.startsAt.slice(0, 5), endsAt: stretch.endsAt.slice(0, 5) })),
    ...Array.from({ length: EMPTY_ROWS }, () => ({ day: '' as const, startsAt: '', endsAt: '' })),
  ];
}

/**
 * The whole week to send (D-28): a row left empty is dropped, which is how a stretch is taken out; a row half filled
 * is refused rather than guessed. Whether the stretches make sense is the server's to say.
 */
export function weekToSend(rows: WeekRow[]): { hours: Stretch[] } | { refusal: string } {
  const hours: Stretch[] = [];
  for (const row of rows) {
    if (!row.day && !row.startsAt && !row.endsAt) continue;
    if (!row.day || !row.startsAt || !row.endsAt) {
      return { refusal: 'Completá día, desde y hasta en cada fila, o dejala vacía para sacarla.' };
    }
    hours.push({ day: row.day, startsAt: row.startsAt, endsAt: row.endsAt });
  }
  return { hours };
}

/** The absences not over yet, at the practice's time `now` — `2026-03-10T09:30` —, in the order they happen. */
export function upcoming(timeOff: Absence[], now: string): Absence[] {
  return timeOff.filter((absence) => absence.endsAt.slice(0, 16) > now);
}

/** `vie 25/09/2026`, `vie 25/09/2026 de 10:30 a 13:00`, or `del lun 28/09/2026 al mié 30/09/2026`. */
export function describeAbsence(absence: Absence): string {
  const wholeDays = timeOf(absence.startsAt) === '00:00' && timeOf(absence.endsAt) === '00:00';
  if (!wholeDays) {
    return `${weekdayDay(absence.startsAt)} de ${timeOf(absence.startsAt)} a ${timeOf(absence.endsAt)}`;
  }
  const last = lastDay(absence.endsAt);
  return last === absence.startsAt.slice(0, 10)
    ? weekdayDay(absence.startsAt)
    : `del ${weekdayDay(absence.startsAt)} al ${weekdayDay(last)}`;
}

/** The day before an absence's end at midnight: its last whole day. */
function lastDay(endsAt: string): string {
  const [year, month, date] = endsAt.slice(0, 10).split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, date - 1)).toISOString().slice(0, 10);
}

/** An absence as it is sent: without times, whole days; with both, part of one day (D-46). */
export function timeOffBody(practitionerId: string, form: { date: string; untilDate: string; startsAt: string; endsAt: string }) {
  return {
    practitionerId,
    date: form.date || null,
    untilDate: form.untilDate || null,
    startsAt: form.startsAt || null,
    endsAt: form.endsAt || null,
  };
}

/** `jue 24/09 10:00 Ana Pérez, jue 24/09 11:00 Juan Gómez`. */
function listed(appointments: TimeOffResult['cancelled'], patients: Record<string, string>): string {
  return appointments
    .map((each) => `${weekdayDay(each.startsAt).slice(0, -5)} ${timeOf(each.startsAt)} ${patients[each.patientId] ?? '(paciente desconocido)'}`)
    .join(', ');
}

/** What the screen says once an absence is recorded, and who is still reception's to handle by hand. */
export function timeOffSaid(result: TimeOffResult): { notice: string; warning: string | null } {
  const notice = result.cancelled.length === 0
    ? 'Ausencia registrada. No tocaba ningún turno.'
    : `Ausencia registrada. Turnos cancelados (${result.cancelled.length}): ${listed(result.cancelled, result.patients)}.`;
  const warning = result.needsManualHandling.length === 0 ? null
    : `Estos pacientes ya llegaron y sus turnos siguen en pie: resolvelos a mano. ${listed(result.needsManualHandling, result.patients)}.`;
  return { notice, warning };
}

/** A refusal by its code, or, for a form the server refused before reading it, the screen's words for that form. */
function withInvalid(words: Record<string, string>, invalid: string): Refusals {
  const known = byCode(words);
  return (error: ApiError) => known(error) ?? (error.status === 400 ? invalid : undefined);
}

const GONE = { PRACTITIONER_NOT_FOUND: 'Eso ya no existe: recargá la pantalla.', REASON_NOT_FOUND: 'Eso ya no existe: recargá la pantalla.' };

export const NAME_REFUSALS = withInvalid(GONE, 'Revisá el nombre: es obligatorio y tiene hasta 150 caracteres.');

export const REASON_REFUSALS = withInvalid({ ...GONE, DUPLICATE_REASON: 'Ya tiene una razón con ese nombre.' },
  'Revisá la razón: el nombre es obligatorio (hasta 60 caracteres) y dura entre 1 y 480 minutos.');

export const WEEK_REFUSALS = withInvalid({
  ...GONE,
  INVALID_WEEKLY_SCHEDULE: 'Dos tramos del mismo día se superponen o se tocan. Sin pausa en el medio, cargalos como un solo tramo.',
}, 'Cada tramo tiene que terminar después de empezar.');

export const TIME_OFF_REFUSALS = withInvalid({
  TIME_OFF_IN_THE_PAST: 'No se registran ausencias en días que ya pasaron.',
  UNKNOWN_PRACTITIONER: 'No se encontró el profesional.',
}, 'Revisá la ausencia: sin horario son días completos; con horario, es un solo día y termina después de empezar.');
