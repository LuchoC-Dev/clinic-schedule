import { describe, expect, it } from 'vitest';

import { appointment } from '../../test/agenda';
import type { Agenda } from '../agenda/agenda';
import { appointmentsToday, type Entry, givableTo, patientLabel, type Queue, timesCalledText } from './arrivals';

const QUEUE: Queue = { id: 'q', name: 'Consultorio 1', room: '1', practitionerIds: ['d1', 'd2'] };
const PEOPLE = [{ id: 'd1', fullName: 'Dra. Arias' }, { id: 'd2', fullName: 'Dr. Benítez' }];

function entry(overrides: Partial<Entry> = {}): Entry {
  return {
    id: 'e1', patientId: 'p1', patient: 'Ana Pérez', patientDocument: 'DNI 30111222', arrivedAt: '2026-03-10T09:10:00',
    emergency: false, appointmentId: null, appointmentAt: null, reservedForId: null, reservedFor: null, state: 'WAITING',
    timesCalled: 0, withPractitionerId: null, withPractitioner: null, ...overrides,
  };
}

describe('a patient in a queue', () => {
  it('is named with their document, or said unknown', () => {
    expect(patientLabel(entry())).toBe('Ana Pérez — DNI 30111222');
    expect(patientLabel(entry({ patientDocument: null }))).toBe('Ana Pérez');
    expect(patientLabel(entry({ patient: null }))).toBe('(paciente desconocido)');
  });

  /** Given to whoever serves the queue, but the one they wait for (D-64). */
  it('may be given to whoever serves the queue but the one they wait for', () => {
    expect(givableTo(entry(), QUEUE, PEOPLE).map((each) => each.fullName)).toEqual(['Dra. Arias', 'Dr. Benítez']);
    expect(givableTo(entry({ reservedForId: 'd1' }), QUEUE, PEOPLE).map((each) => each.id)).toEqual(['d2']);
    expect(givableTo(entry(), { ...QUEUE, practitionerIds: ['d9'] }, PEOPLE))
      .toEqual([{ id: 'd9', fullName: '(profesional desconocido)' }]);
  });

  it('says how many times they were called, from the first', () => {
    expect(timesCalledText(0)).toBeNull();
    expect(timesCalledText(1)).toBe('Llamado 1 vez');
    expect(timesCalledText(2)).toBe('Llamado 2 veces');
  });
});

/** Their appointments of today with whoever serves the queue, still booked (D-64). */
describe('the appointments a patient found comes in with', () => {
  const today = {
    date: '2026-03-10', from: '2026-03-10', to: '2026-03-10', practitionerId: null, practitioners: [],
    appointments: [
      appointment({ id: 'a1', patientId: 'p1', practitionerId: 'd1', startsAt: '2026-03-10T10:00:00' }),
      appointment({ id: 'a2', patientId: 'p1', practitionerId: 'd3', startsAt: '2026-03-10T11:00:00' }),
      appointment({ id: 'a3', patientId: 'p1', practitionerId: 'd2', startsAt: '2026-03-10T12:00:00', status: 'ABSENT' }),
      appointment({ id: 'a4', patientId: 'p2', practitionerId: 'd1', startsAt: '2026-03-10T13:00:00' }),
    ],
  } as Agenda;

  it('are theirs, with a practitioner of the queue, and not absent, each by its time', () => {
    expect(appointmentsToday('p1', today, QUEUE)).toEqual([{ appointment: today.appointments[0], label: '10:00' }]);
    expect(appointmentsToday('p9', today, QUEUE)).toEqual([]);
    expect(appointmentsToday('p1', undefined, QUEUE)).toEqual([]);
  });
});
