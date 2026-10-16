import type { ApiError } from '../../shared/api';
import { addDays, weekdayDay } from '../../shared/dates';
import { byCode, combine } from '../../shared/refusals';
import { dayAndTime } from '../words';

/**
 * What the patients' appointments, booking and moving decide (D-80, D-82, D-83, D-85, D-93): each appointment's
 * state in words, the days the web offers, and each refusal in the Thymeleaf screens' words — naming the member it
 * was for, when it was not the patient's own.
 */

export type State = 'BOOKED' | 'CANCELLED' | 'ABSENT';

export interface Entry {
  id: string;
  startsAt: string;
  practitioner: { id: string; fullName: string | null };
  reason: string;
  state: State;
  cancellationRequestedBy: 'PATIENT' | 'PRACTICE' | null;
  /** Whether it may still be cancelled on the web, as the server's rules say (D-111). */
  cancellable: boolean;
  movable: boolean;
}

export interface OwnAppointments {
  upcoming: Entry[];
  past: Entry[];
  family: { patientId: string; fullName: string; upcoming: Entry[]; past: Entry[] }[];
}

export interface BookingOptions {
  firstDay: string;
  lastDay: string;
  practitioners: { id: string; fullName: string; reasons: { id: string; name: string; durationMinutes: number }[] }[];
  /** Whom the patient books for: themselves first, then the members they act for (D-85). */
  patients: { id: string; fullName: string }[];
}

export interface Times {
  date: string;
  times: string[];
}

/** The appointment to move, as it stands (D-111). */
export interface MoveTarget {
  id: string;
  member: string | null;
  startsAt: string;
  practitioner: { id: string; fullName: string | null };
  reason: string;
  movable: boolean;
  firstDay: string;
  lastDay: string;
}

/** What booking or moving answers: for whom, when, with whom, and whether the email went out. */
export interface Done {
  startsAt: string;
  practitioner: string;
  confirmationSent: boolean;
}

/** An appointment's state, as the patient reads it of their own (`own`) or of a member's. */
export function stateOf(entry: Entry, own: boolean): string {
  switch (entry.state) {
    case 'BOOKED':
      return 'Reservado';
    case 'ABSENT':
      return own ? 'No asististe' : 'No asistió';
    case 'CANCELLED':
      if (entry.cancellationRequestedBy === 'PATIENT') {
        return own ? 'Cancelado a tu pedido' : 'Cancelado a pedido del paciente';
      }
      return entry.cancellationRequestedBy === 'PRACTICE' ? 'Cancelado por el consultorio' : 'Cancelado';
  }
}

/** Every day from the first to the last, both as `2026-03-11`. */
export function daysBetween(first: string, last: string): string[] {
  const days: string[] = [];
  for (let day = first; day <= last; day = addDays(day, 1)) {
    days.push(day);
  }
  return days;
}

/** `jue` and `12`, as the approved mock's day buttons say it. */
export function dayChip(day: string): { weekday: string; number: string } {
  return { weekday: weekdayDay(day).split(' ')[0], number: String(Number(day.slice(8, 10))) };
}

/** `Desde mañana y hasta el 9/04/2026.` */
export function untilSaid(lastDay: string): string {
  const [year, month, date] = lastDay.split('-');
  return `Desde mañana y hasta el ${Number(date)}/${month}/${year}`;
}

/** What Mis turnos says once booked or moved, as the Thymeleaf screens said it. */
export function doneSaid(what: 'reservado' | 'cambiado', done: Done, member: string | null): string {
  return `Turno ${what}${member ? ` para ${member}` : ''}: ${dayAndTime(done.startsAt)} con ${done.practitioner}`
    + (done.confirmationSent ? '. Te mandamos la confirmación por email.'
      : `. No pudimos mandarte la confirmación por email, pero el turno quedó ${what}.`);
}

export const CANCEL_REFUSALS = byCode({
  NOT_BOOKED: 'Ese turno ya no está reservado.',
  TOO_LATE: 'Falta menos de un día para el turno: para cancelarlo, llamá o acercate a recepción.',
});

/** A booking refused, naming the member it was for. */
export function bookingRefusals(member: string | null) {
  return combine((error: ApiError) => {
    if (!member) {
      return undefined;
    }
    switch (error.body?.code) {
      case 'TOO_MANY_AHEAD':
        return `${member} ya tiene 3 turnos por delante: para sacar otro, llamá o acercate a recepción.`;
      case 'PATIENT_BUSY':
        return `${member} ya tiene otro turno a esa hora.`;
      default:
        return undefined;
    }
  }, byCode({
    NOT_ON_THE_WEB: 'Ese motivo no se reserva por la web: llamá o acercate a recepción.',
    DAY_OUT_OF_RANGE: 'Ese día no se reserva por la web: elegí uno entre mañana y el último que ofrece la pantalla.',
    NOT_AN_OFFERED_TIME: 'Ese horario ya no está libre: elegí otro.',
    TIME_TAKEN: 'Ese horario ya no está libre: elegí otro.',
    TOO_MANY_AHEAD: 'Ya tenés 3 turnos por delante: para sacar otro, llamá o acercate a recepción.',
    ONE_AHEAD_UNTIL_VERIFIED: 'Hasta que recepción verifique tu identidad podés tener un turno por delante reservado '
      + 'por la web. Acercate con tu documento, o llamá para sacar otro.',
    PATIENT_BUSY: 'Ya tenés otro turno a esa hora.',
  }));
}

export const MOVE_REFUSALS = byCode({
  NOT_BOOKED: 'Ese turno ya no está reservado.',
  TOO_LATE: 'Falta menos de un día para el turno: para cambiarlo, llamá o acercate a recepción.',
  DAY_OUT_OF_RANGE: 'Ese día no se puede elegir por la web: elegí uno entre mañana y el último que ofrece la pantalla.',
  SAME_TIME: 'El turno ya es a esa hora: elegí otra.',
  NOT_AN_OFFERED_TIME: 'Ese horario ya no está libre: elegí otro.',
  TIME_TAKEN: 'Ese horario ya no está libre: elegí otro.',
  PATIENT_BUSY: 'Ya hay otro turno del paciente a esa hora: elegí otra.',
});
