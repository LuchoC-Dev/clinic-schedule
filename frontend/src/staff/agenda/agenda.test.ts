import { describe, expect, it } from 'vitest';

import { appointment } from '../../test/agenda';
import { patientName, stateOf, summaryOf } from './agenda';

describe('the state of an appointment', () => {
  it('is booked until something happens', () => {
    expect(stateOf(appointment())).toEqual({ label: 'Reservado', tone: 'booked' });
  });

  it('says the patient warned', () => {
    expect(stateOf(appointment({ lateArrivalNotice: true }))).toEqual({ label: 'Avisó que llega tarde', tone: 'late' });
  });

  it('says when the patient arrived, even after warning', () => {
    expect(stateOf(appointment({ lateArrivalNotice: true, arrivedAt: '2026-03-10T09:12:41' })))
      .toEqual({ label: 'Llegó a las 09:12', tone: 'arrived' });
  });

  it('is absent above all, which is final', () => {
    expect(stateOf(appointment({ status: 'ABSENT', lateArrivalNotice: true })))
      .toEqual({ label: 'Ausente', tone: 'absent' });
  });
});

describe("the day's count", () => {
  it('says an empty day so', () => {
    expect(summaryOf([])).toBe('No hay turnos reservados.');
  });

  it('counts the appointments, who arrived and who was absent, leaving out what is zero', () => {
    expect(summaryOf([appointment()])).toBe('1 turno');
    expect(summaryOf([
      appointment(), appointment({ arrivedAt: '2026-03-10T09:00:00' }),
      appointment({ arrivedAt: '2026-03-10T09:30:00' }), appointment({ status: 'ABSENT' }),
    ])).toBe('4 turnos · 2 llegaron · 1 ausente');
    expect(summaryOf([appointment({ arrivedAt: '2026-03-10T09:00:00' }), appointment({ status: 'ABSENT' }),
      appointment({ status: 'ABSENT' })])).toBe('3 turnos · 1 llegó · 2 ausentes');
  });
});

describe("a patient's name", () => {
  it('is said unknown when the patient module no longer knows them', () => {
    expect(patientName(appointment())).toBe('Ana Pérez');
    expect(patientName(appointment({ patient: null }))).toBe('(paciente desconocido)');
  });
});
