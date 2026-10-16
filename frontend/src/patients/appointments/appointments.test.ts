import { describe, expect, it } from 'vitest';

import { ApiError } from '../../shared/api';
import { describeError } from '../../shared/errors';
import {
  bookingRefusals, CANCEL_REFUSALS, dayChip, daysBetween, doneSaid, type Entry, MOVE_REFUSALS, stateOf, untilSaid,
} from './appointments';

const refusal = (code: string) => new ApiError(409, { status: 409, error: 'Conflict', message: 'in English', code });

function entry(changes: Partial<Entry>): Entry {
  return {
    id: 'a1', startsAt: '2026-03-12T10:00:00', practitioner: { id: 'p1', fullName: 'Dra. Arias' }, reason: 'consulta',
    state: 'BOOKED', cancellationRequestedBy: null, cancellable: false, movable: false, ...changes,
  };
}

describe("an appointment's state", () => {
  it('is said to the patient of their own, and of a member as of someone else', () => {
    expect(stateOf(entry({}), true)).toBe('Reservado');
    expect(stateOf(entry({ state: 'ABSENT' }), true)).toBe('No asististe');
    expect(stateOf(entry({ state: 'ABSENT' }), false)).toBe('No asistió');
    expect(stateOf(entry({ state: 'CANCELLED', cancellationRequestedBy: 'PATIENT' }), true)).toBe('Cancelado a tu pedido');
    expect(stateOf(entry({ state: 'CANCELLED', cancellationRequestedBy: 'PATIENT' }), false))
      .toBe('Cancelado a pedido del paciente');
    expect(stateOf(entry({ state: 'CANCELLED', cancellationRequestedBy: 'PRACTICE' }), true))
      .toBe('Cancelado por el consultorio');
    expect(stateOf(entry({ state: 'CANCELLED' }), true)).toBe('Cancelado');
  });
});

describe('the days the web offers', () => {
  it('are every day from the first to the last, across a month', () => {
    expect(daysBetween('2026-03-30', '2026-04-02')).toEqual(['2026-03-30', '2026-03-31', '2026-04-01', '2026-04-02']);
    expect(daysBetween('2026-03-11', '2026-03-11')).toEqual(['2026-03-11']);
  });

  it("are said by the practice's weekday and number, whatever the browser's zone", () => {
    expect(dayChip('2026-03-12')).toEqual({ weekday: 'jue', number: '12' });
    expect(dayChip('2026-03-01')).toEqual({ weekday: 'dom', number: '1' });
  });

  it('end on the last one, as the Thymeleaf screen said it', () => {
    expect(untilSaid('2026-04-09')).toBe('Desde mañana y hasta el 9/04/2026');
  });
});

describe('what is said once booked or moved', () => {
  it('names the member, when, with whom, and whether the email went out', () => {
    const done = { startsAt: '2026-03-12T10:00:00', practitioner: 'Dra. Arias', confirmationSent: true };
    expect(doneSaid('reservado', done, null))
      .toBe('Turno reservado: Jueves 12 de marzo, 10:00 con Dra. Arias. Te mandamos la confirmación por email.');
    expect(doneSaid('cambiado', { ...done, confirmationSent: false }, 'Benjamín Medina'))
      .toBe('Turno cambiado para Benjamín Medina: Jueves 12 de marzo, 10:00 con Dra. Arias. No pudimos mandarte la '
        + 'confirmación por email, pero el turno quedó cambiado.');
  });
});

describe('the refusals', () => {
  it("of a booking name the member it was for, and otherwise speak to the patient", () => {
    expect(describeError(refusal('TOO_MANY_AHEAD'), bookingRefusals('Benjamín Medina')))
      .toBe('Benjamín Medina ya tiene 3 turnos por delante: para sacar otro, llamá o acercate a recepción.');
    expect(describeError(refusal('PATIENT_BUSY'), bookingRefusals('Benjamín Medina')))
      .toBe('Benjamín Medina ya tiene otro turno a esa hora.');
    expect(describeError(refusal('TOO_MANY_AHEAD'), bookingRefusals(null)))
      .toBe('Ya tenés 3 turnos por delante: para sacar otro, llamá o acercate a recepción.');
    expect(describeError(refusal('PATIENT_BUSY'), bookingRefusals(null))).toBe('Ya tenés otro turno a esa hora.');
    expect(describeError(refusal('TIME_TAKEN'), bookingRefusals('Benjamín Medina')))
      .toBe('Ese horario ya no está libre: elegí otro.');
    expect(describeError(refusal('ONE_AHEAD_UNTIL_VERIFIED'), bookingRefusals(null)))
      .toContain('Hasta que recepción verifique tu identidad');
  });

  it("of cancelling and moving are the Thymeleaf screens'", () => {
    expect(describeError(refusal('TOO_LATE'), CANCEL_REFUSALS)).toContain('para cancelarlo, llamá');
    expect(describeError(refusal('TOO_LATE'), MOVE_REFUSALS)).toContain('para cambiarlo, llamá');
    expect(describeError(refusal('SAME_TIME'), MOVE_REFUSALS)).toBe('El turno ya es a esa hora: elegí otra.');
    expect(describeError(refusal('PATIENT_BUSY'), MOVE_REFUSALS))
      .toBe('Ya hay otro turno del paciente a esa hora: elegí otra.');
  });
});
