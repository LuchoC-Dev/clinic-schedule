/**
 * What reception's agenda of the day decides (D-98): each appointment's state in words, the day's count, and
 * how a patient is named. The rules stay in the server; this only says what it answered.
 */

export type AppointmentStatus = 'BOOKED' | 'CANCELLED' | 'ABSENT';

export interface AgendaAppointment {
  id: string;
  startsAt: string;
  endsAt: string;
  durationMinutes: number;
  patientId: string;
  patient: string | null;
  patientDocument: string | null;
  practitionerId: string;
  practitioner: string | null;
  reason: string;
  note: string | null;
  status: AppointmentStatus;
  arrivedAt: string | null;
  lateArrivalNotice: boolean;
  bookedVia: 'RECEPTION' | 'WEB';
  movedOnWeb: boolean;
  /** Who cancelled it, when, how and who asked (D-76); null unless it is cancelled. */
  cancellation: Cancellation | null;
}

export interface Cancellation {
  at: string | null;
  by: string | null;
  origin: 'BY_HAND' | 'TIME_OFF' | 'ONLINE' | 'EMAIL_LINK';
  requestedBy: 'PATIENT' | 'PRACTICE' | null;
  note: string | null;
}

export interface Agenda {
  date: string;
  /** The days it spans: the day alone, or its week from Monday to Sunday (D-100). */
  from: string;
  to: string;
  /** Whose agenda it is; null for everyone's. */
  practitionerId: string | null;
  practitioners: { id: string; fullName: string }[];
  appointments: AgendaAppointment[];
}

export type Tone = 'booked' | 'late' | 'arrived' | 'absent' | 'cancelled';

/**
 * As the Thymeleaf agenda said it: absent is final (D-61); then whether the patient arrived, into their
 * practitioner's queue (D-65); then whether they warned they would be late (D-31).
 */
export function stateOf(appointment: AgendaAppointment): { label: string; tone: Tone } {
  if (appointment.status === 'ABSENT') {
    return { label: 'Ausente', tone: 'absent' };
  }
  if (appointment.status === 'CANCELLED') {
    return { label: 'Cancelado', tone: 'cancelled' };
  }
  if (appointment.arrivedAt) {
    return { label: `Llegó a las ${appointment.arrivedAt.slice(11, 16)}`, tone: 'arrived' };
  }
  if (appointment.lateArrivalNotice) {
    return { label: 'Avisó que llega tarde', tone: 'late' };
  }
  return { label: 'Reservado', tone: 'booked' };
}

/** `14 turnos · 5 llegaron · 1 ausente`; what is zero is not said, and an empty day says so. */
export function summaryOf(appointments: AgendaAppointment[]): string {
  if (appointments.length === 0) {
    return 'No hay turnos reservados.';
  }
  const arrived = appointments.filter((each) => each.status !== 'ABSENT' && each.arrivedAt).length;
  const absent = appointments.filter((each) => each.status === 'ABSENT').length;
  const parts = [count(appointments.length, 'turno', 'turnos')];
  if (arrived > 0) {
    parts.push(count(arrived, 'llegó', 'llegaron'));
  }
  if (absent > 0) {
    parts.push(count(absent, 'ausente', 'ausentes'));
  }
  return parts.join(' · ');
}

function count(n: number, one: string, many: string) {
  return `${n} ${n === 1 ? one : many}`;
}

/** The patient's name; one the patient module no longer knows is said so, as on the screens. */
export function patientName(appointment: AgendaAppointment): string {
  return appointment.patient ?? '(paciente desconocido)';
}

/**
 * What reception may do with an appointment, as on the Thymeleaf agenda: nothing once it is not booked — an absent
 * one is a record (D-61), a cancelled one too —; once the patient arrived, only cancel it; warning they will be
 * late, once. Whatever else is refused, the server refuses it anyway.
 */
export function actionsFor(appointment: AgendaAppointment) {
  const booked = appointment.status === 'BOOKED';
  const arrived = appointment.arrivedAt !== null;
  return {
    arrive: booked && !arrived,
    warnLate: booked && !arrived && !appointment.lateArrivalNotice,
    move: booked && !arrived,
    cancel: booked,
  };
}

/** The days an agenda spans, each with its appointments, in order: one, or the seven of its week. */
export function daysOf(agenda: Pick<Agenda, 'from' | 'to' | 'appointments'>, addDays: (day: string, n: number) => string) {
  const days: { day: string; appointments: AgendaAppointment[] }[] = [];
  for (let day = agenda.from; day <= agenda.to; day = addDays(day, 1)) {
    days.push({ day, appointments: agenda.appointments.filter((each) => each.startsAt.slice(0, 10) === day) });
  }
  return days;
}
