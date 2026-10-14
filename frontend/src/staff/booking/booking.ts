/**
 * What the booking and moving screens decide (D-36, D-45, D-75): the time chosen — one of the free ones the agenda
 * offers, or another typed by hand —, and whose the booking is — a patient found, or a new one registered at once.
 */

export interface FreeTimes {
  date: string;
  practitionerWorks: boolean;
  /** `09:30:00`. */
  times: string[];
}

/** `09:30` of `09:30:00`. */
export function hourOf(time: string): string {
  return time.slice(0, 5);
}

/**
 * When it starts: another time typed by hand wins over one of the free ones, as on the Thymeleaf screens; null when
 * neither is chosen.
 */
export function startOf(date: string, chosen: string | null, typed: string): string | null {
  const time = typed.trim() || (chosen ? hourOf(chosen) : '');
  return /^\d{2}:\d{2}$/.test(time) ? `${date}T${time}:00` : null;
}

/** What the free times say when there are none. */
export function noTimesSaid(freeTimes: FreeTimes, practitioner: string): string | null {
  if (freeTimes.times.length > 0) {
    return null;
  }
  return freeTimes.practitionerWorks
    ? 'No quedan horarios libres ese día para esta razón.'
    : `${practitioner} no atiende ese día.`;
}

/** Why it cannot be booked yet, or null when it can. */
export function missing(booking: { practitionerId: string; reasonId: string; start: string | null;
  patientId: string | null; newPatientName: string; registering: boolean }): string | null {
  if (!booking.practitionerId) return 'Elegí el profesional.';
  if (!booking.reasonId) return 'Elegí la razón.';
  if (booking.registering ? !booking.newPatientName.trim() : !booking.patientId) {
    return booking.registering ? 'Escribí el nombre del paciente nuevo.' : 'Elegí el paciente.';
  }
  if (!booking.start) return 'Elegí un horario.';
  return null;
}
