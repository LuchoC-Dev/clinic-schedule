import { describe, expect, it } from 'vitest';

import { ApiError } from '../../shared/api';
import { describeError } from '../../shared/errors';
import type { Practitioner } from '../types';
import {
  describeAbsence, REASON_REFUSALS, TIME_OFF_REFUSALS, timeOffBody, timeOffSaid, upcoming, WEEK_REFUSALS, weekRows, weekToSend,
} from './practitioners';
import { available, queueBody, servedBy } from './queues';

const refused = (status: number, code?: string) => new ApiError(status, { status, error: 'x', message: 'english', code });

describe("a practitioner's week", () => {
  it('is edited as its stretches and three empty rows', () => {
    expect(weekRows([{ day: 'MONDAY', startsAt: '09:00:00', endsAt: '13:00:00' }])).toEqual([
      { day: 'MONDAY', startsAt: '09:00', endsAt: '13:00' },
      { day: '', startsAt: '', endsAt: '' }, { day: '', startsAt: '', endsAt: '' }, { day: '', startsAt: '', endsAt: '' },
    ]);
  });

  it('is sent whole, an empty row dropped', () => {
    expect(weekToSend([
      { day: 'MONDAY', startsAt: '09:00', endsAt: '13:00' },
      { day: '', startsAt: '', endsAt: '' },
      { day: 'MONDAY', startsAt: '14:00', endsAt: '18:00' },
    ])).toEqual({ hours: [{ day: 'MONDAY', startsAt: '09:00', endsAt: '13:00' }, { day: 'MONDAY', startsAt: '14:00', endsAt: '18:00' }] });
    expect(weekToSend([{ day: '', startsAt: '', endsAt: '' }])).toEqual({ hours: [] });
  });

  it('refuses a row half filled rather than guess it', () => {
    const refusal = { refusal: 'Completá día, desde y hasta en cada fila, o dejala vacía para sacarla.' };
    expect(weekToSend([{ day: 'MONDAY', startsAt: '09:00', endsAt: '' }])).toEqual(refusal);
    expect(weekToSend([{ day: '', startsAt: '09:00', endsAt: '13:00' }])).toEqual(refusal);
    expect(weekToSend([{ day: 'MONDAY', startsAt: '', endsAt: '13:00' }])).toEqual(refusal);
  });

  it("says each refusal in the practitioner screen's words, a form refused unread too", () => {
    expect(describeError(refused(400, 'INVALID_WEEKLY_SCHEDULE'), WEEK_REFUSALS)).toMatch(/^Dos tramos del mismo día/);
    expect(describeError(refused(400, 'ILLEGAL_ARGUMENT'), WEEK_REFUSALS)).toBe('Cada tramo tiene que terminar después de empezar.');
    expect(describeError(refused(409, 'DUPLICATE_REASON'), REASON_REFUSALS)).toBe('Ya tiene una razón con ese nombre.');
    expect(describeError(refused(400), REASON_REFUSALS)).toMatch(/^Revisá la razón/);
    expect(describeError(refused(404, 'REASON_NOT_FOUND'), REASON_REFUSALS)).toBe('Eso ya no existe: recargá la pantalla.');
    expect(describeError(refused(409, 'SOMETHING_ELSE'), REASON_REFUSALS)).not.toMatch(/^Revisá/);
  });
});

describe("a practitioner's time off", () => {
  it('shows those not over yet at the practice', () => {
    const over = { startsAt: '2026-03-09T00:00:00', endsAt: '2026-03-10T00:00:00' };
    const today = { startsAt: '2026-03-10T08:00:00', endsAt: '2026-03-10T10:00:00' };
    const later = { startsAt: '2026-03-12T00:00:00', endsAt: '2026-03-13T00:00:00' };
    expect(upcoming([over, today, later], '2026-03-10T09:30')).toEqual([today, later]);
    expect(upcoming([today], '2026-03-10T10:00')).toEqual([]);
  });

  it('says a whole day, several, or part of one', () => {
    expect(describeAbsence({ startsAt: '2026-09-25T00:00:00', endsAt: '2026-09-26T00:00:00' })).toBe('vie 25/09/2026');
    expect(describeAbsence({ startsAt: '2026-09-28T00:00:00', endsAt: '2026-10-01T00:00:00' }))
      .toBe('del lun 28/09/2026 al mié 30/09/2026');
    expect(describeAbsence({ startsAt: '2026-09-25T10:30:00', endsAt: '2026-09-25T13:00:00' })).toBe('vie 25/09/2026 de 10:30 a 13:00');
    expect(describeAbsence({ startsAt: '2026-09-25T00:00:00', endsAt: '2026-09-25T13:00:00' })).toBe('vie 25/09/2026 de 00:00 a 13:00');
    expect(describeAbsence({ startsAt: '2026-09-25T18:00:00', endsAt: '2026-09-26T00:00:00' })).toBe('vie 25/09/2026 de 18:00 a 00:00');
  });

  it('is sent with what was typed, the rest as nothing', () => {
    expect(timeOffBody('p1', { date: '2026-03-10', untilDate: '', startsAt: '11:00', endsAt: '13:00' }))
      .toEqual({ practitionerId: 'p1', date: '2026-03-10', untilDate: null, startsAt: '11:00', endsAt: '13:00' });
    expect(timeOffBody('p1', { date: '2026-03-10', untilDate: '2026-03-12', startsAt: '', endsAt: '' }))
      .toEqual({ practitionerId: 'p1', date: '2026-03-10', untilDate: '2026-03-12', startsAt: null, endsAt: null });
  });

  it('says what it cancelled, by name, and who arrived already and is to handle by hand', () => {
    const said = timeOffSaid({
      cancelled: [{ id: 'a1', patientId: 'p1', startsAt: '2026-09-24T10:00:00' }, { id: 'a2', patientId: 'p2', startsAt: '2026-09-24T11:00:00' }],
      needsManualHandling: [{ id: 'a3', patientId: 'p3', startsAt: '2026-09-24T09:00:00' }],
      patients: { p1: 'Ana Pérez', p2: 'Juan Gómez', p3: 'Lía Paz' },
    });
    expect(said.notice).toBe('Ausencia registrada. Turnos cancelados (2): jue 24/09 10:00 Ana Pérez, jue 24/09 11:00 Juan Gómez.');
    expect(said.warning).toBe('Estos pacientes ya llegaron y sus turnos siguen en pie: resolvelos a mano. jue 24/09 09:00 Lía Paz.');
    expect(timeOffSaid({ cancelled: [], needsManualHandling: [], patients: {} }))
      .toEqual({ notice: 'Ausencia registrada. No tocaba ningún turno.', warning: null });
  });

  it('says an absence in the past, and one that makes no sense', () => {
    expect(describeError(refused(400, 'TIME_OFF_IN_THE_PAST'), TIME_OFF_REFUSALS)).toBe('No se registran ausencias en días que ya pasaron.');
    expect(describeError(refused(400, 'ILLEGAL_ARGUMENT'), TIME_OFF_REFUSALS)).toMatch(/^Revisá la ausencia/);
  });
});

describe('the queues', () => {
  const practitioners: Practitioner[] = [
    { id: 'p1', fullName: 'Dra. Arias', reasons: [] }, { id: 'p2', fullName: 'Dr. Benítez', reasons: [] },
    { id: 'p3', fullName: 'Lic. Paz', reasons: [] },
  ];

  it('offer only who serves no queue yet', () => {
    const queues = [{ id: 'q1', name: 'Guardia', room: null, practitionerIds: ['p1'] },
      { id: 'q2', name: 'Clínica', room: '2', practitionerIds: ['p3'] }];
    expect(available(practitioners, queues).map((each) => each.id)).toEqual(['p2']);
  });

  it('name who serves them, one unknown said so', () => {
    expect(servedBy({ id: 'q', name: 'G', room: null, practitionerIds: ['p2', 'x'] }, practitioners))
      .toEqual(['Dr. Benítez', '(profesional desconocido)']);
  });

  it('are sent with no room when none was typed', () => {
    expect(queueBody({ name: ' Guardia ', room: ' ' })).toEqual({ name: 'Guardia', room: null });
    expect(queueBody({ name: 'Clínica', room: ' 3 ' })).toEqual({ name: 'Clínica', room: '3' });
  });
});
