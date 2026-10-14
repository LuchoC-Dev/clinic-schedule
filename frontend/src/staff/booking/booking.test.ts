import { describe, expect, it } from 'vitest';

import { missing, noTimesSaid, startOf } from './booking';

describe('the time chosen', () => {
  it('is one of the free times, or another typed by hand, which wins', () => {
    expect(startOf('2026-03-10', '09:30:00', '')).toBe('2026-03-10T09:30:00');
    expect(startOf('2026-03-10', '09:30:00', '11:15')).toBe('2026-03-10T11:15:00');
    expect(startOf('2026-03-10', null, '11:15')).toBe('2026-03-10T11:15:00');
  });

  it('is none when nothing is chosen', () => {
    expect(startOf('2026-03-10', null, '')).toBeNull();
    expect(startOf('2026-03-10', null, '  ')).toBeNull();
  });
});

/** A day not worked is said apart from a full one (D-45). */
describe('a day without free times', () => {
  it('says the practitioner does not work it, or that it is full', () => {
    expect(noTimesSaid({ date: '2026-03-11', practitionerWorks: false, times: [] }, 'Dra. Arias'))
      .toBe('Dra. Arias no atiende ese día.');
    expect(noTimesSaid({ date: '2026-03-10', practitionerWorks: true, times: [] }, 'Dra. Arias'))
      .toBe('No quedan horarios libres ese día para esta razón.');
    expect(noTimesSaid({ date: '2026-03-10', practitionerWorks: true, times: ['09:00:00'] }, 'Dra. Arias')).toBeNull();
  });
});

describe('what a booking still lacks', () => {
  const ready = { practitionerId: 'd1', reasonId: 'r1', start: '2026-03-10T09:00:00', patientId: 'p1',
    newPatientName: '', registering: false };

  it('is nothing once it has a practitioner, a reason, a patient and a time', () => {
    expect(missing(ready)).toBeNull();
    expect(missing({ ...ready, patientId: null, registering: true, newPatientName: 'Rosa' })).toBeNull();
  });

  it('is said in the order the screen asks for it', () => {
    expect(missing({ ...ready, practitionerId: '' })).toBe('Elegí el profesional.');
    expect(missing({ ...ready, reasonId: '' })).toBe('Elegí la razón.');
    expect(missing({ ...ready, patientId: null })).toBe('Elegí el paciente.');
    expect(missing({ ...ready, registering: true, newPatientName: '  ' })).toBe('Escribí el nombre del paciente nuevo.');
    expect(missing({ ...ready, start: null })).toBe('Elegí un horario.');
  });
});
