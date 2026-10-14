import type { AgendaAppointment } from '../staff/agenda/agenda';

/** An appointment of the agenda as the API answers it, booked and with nothing happened yet. */
export function appointment(overrides: Partial<AgendaAppointment> = {}): AgendaAppointment {
  return {
    id: 'a1', startsAt: '2026-03-10T09:00:00', endsAt: '2026-03-10T09:30:00', durationMinutes: 30,
    patientId: 'p1', patient: 'Ana Pérez', patientDocument: 'DNI 30111222',
    practitionerId: 'd1', practitioner: 'Dra. Laura Arias', reason: 'consulta', note: null,
    status: 'BOOKED', arrivedAt: null, lateArrivalNotice: false, bookedVia: 'RECEPTION', movedOnWeb: false,
    cancellation: null,
    ...overrides,
  };
}
