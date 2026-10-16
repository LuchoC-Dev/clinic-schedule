import { timeOf } from '../../shared/dates';
import type { Agenda, AgendaAppointment } from '../agenda/agenda';

/** A queue as its set-up says it (D-62): who serves it. */
export interface Queue {
  id: string;
  name: string;
  room: string | null;
  practitionerIds: string[];
}

/** A patient's place in a queue (D-63, D-64), with the names the applications show (D-102). */
export interface Entry {
  id: string;
  patientId: string;
  patient: string | null;
  patientDocument: string | null;
  arrivedAt: string;
  emergency: boolean;
  appointmentId: string | null;
  appointmentAt: string | null;
  reservedForId: string | null;
  reservedFor: string | null;
  state: string;
  timesCalled: number;
  withPractitionerId: string | null;
  withPractitioner: string | null;
}

export interface Session {
  queueId: string;
  open: boolean;
  closesByItself: boolean | null;
  waiting: Entry[];
  uncallable: Entry[];
  called: Entry[];
  done: Entry[];
}

/** `Ana Pérez — DNI 30111222`, as the queue screens name a patient. */
export function patientLabel(entry: Pick<Entry, 'patient' | 'patientDocument'>): string {
  if (!entry.patient) {
    return '(paciente desconocido)';
  }
  return entry.patientDocument ? `${entry.patient} — ${entry.patientDocument}` : entry.patient;
}

/** Whom a waiting patient may be given to: whoever serves the queue, but the one they already wait for (D-64). */
export function givableTo(entry: Entry, queue: Queue, practitioners: { id: string; fullName: string }[]) {
  return queue.practitionerIds
    .filter((id) => id !== entry.reservedForId)
    .map((id) => ({ id, fullName: practitioners.find((each) => each.id === id)?.fullName ?? '(profesional desconocido)' }));
}

/** `Llamado 1 vez`, `Llamado 2 veces`, or nothing before the first call. */
export function timesCalledText(times: number): string | null {
  if (times === 0) {
    return null;
  }
  return `Llamado ${times} ${times === 1 ? 'vez' : 'veces'}`;
}

/**
 * The appointments a patient found may come in with today (D-64): theirs, of today, with a practitioner who serves
 * the queue, not cancelled nor absent — as the Thymeleaf screen offered them. Each is said by its time.
 */
export function appointmentsToday(patientId: string, today: Agenda | undefined, queue: Queue)
  : { appointment: AgendaAppointment; label: string }[] {
  return (today?.appointments ?? [])
    .filter((each) => each.patientId === patientId && each.status === 'BOOKED'
      && queue.practitionerIds.includes(each.practitionerId))
    .map((appointment) => ({ appointment, label: timeOf(appointment.startsAt) }));
}
