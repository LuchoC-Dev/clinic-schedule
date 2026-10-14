import { describe, expect, it } from 'vitest';

import { appointment } from '../../test/agenda';
import { addDays } from '../../shared/dates';
import { actionsFor, daysOf, patientName, stateOf, summaryOf } from './agenda';

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

describe('what reception may do with an appointment', () => {
  it('arrives, warns, moves and cancels one still booked', () => {
    expect(actionsFor(appointment())).toEqual({ arrive: true, warnLate: true, move: true, cancel: true });
  });

  it('warns only once', () => {
    expect(actionsFor(appointment({ lateArrivalNotice: true })))
      .toEqual({ arrive: true, warnLate: false, move: true, cancel: true });
  });

  it('only cancels one whose patient arrived', () => {
    expect(actionsFor(appointment({ arrivedAt: '2026-03-10T09:05:00' })))
      .toEqual({ arrive: false, warnLate: false, move: false, cancel: true });
  });

  it('does nothing with one absent or cancelled, which are records (D-61)', () => {
    const nothing = { arrive: false, warnLate: false, move: false, cancel: false };
    expect(actionsFor(appointment({ status: 'ABSENT' }))).toEqual(nothing);
    expect(actionsFor(appointment({ status: 'CANCELLED' }))).toEqual(nothing);
  });

  it('says a cancelled one so', () => {
    expect(stateOf(appointment({ status: 'CANCELLED', lateArrivalNotice: true })))
      .toEqual({ label: 'Cancelado', tone: 'cancelled' });
  });
});

describe('the days of an agenda', () => {
  it('are each day from the first to the last, with its appointments in order', () => {
    const days = daysOf({
      from: '2026-03-09', to: '2026-03-15',
      appointments: [appointment({ id: 'a', startsAt: '2026-03-10T09:00:00' }),
        appointment({ id: 'b', startsAt: '2026-03-10T11:00:00' }), appointment({ id: 'c', startsAt: '2026-03-15T09:00:00' })],
    }, addDays);

    expect(days.map((each) => each.day)).toEqual(['2026-03-09', '2026-03-10', '2026-03-11', '2026-03-12', '2026-03-13',
      '2026-03-14', '2026-03-15']);
    expect(days[1].appointments.map((each) => each.id)).toEqual(['a', 'b']);
    expect(days[6].appointments.map((each) => each.id)).toEqual(['c']);
    expect(days[0].appointments).toEqual([]);
  });

  it('are one for a day', () => {
    expect(daysOf({ from: '2026-03-10', to: '2026-03-10', appointments: [] }, addDays).map((each) => each.day))
      .toEqual(['2026-03-10']);
  });
});
