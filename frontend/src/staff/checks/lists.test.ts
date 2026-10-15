import { describe, expect, it } from 'vitest';

import { ApiError } from '../../shared/api';
import { describeError } from '../../shared/errors';
import { CHECK_ACCESS_REFUSALS, grantedUntil, reasonLabel } from '../record/access';
import { type MyOrder, orderSituation, patientOf, splitOrders } from './lists';

function order(overrides: Partial<MyOrder> = {}): MyOrder {
  return {
    orderId: 'o1', patientId: 'p1', patientName: 'Ana Pérez', patientDocument: 'DNI 30111222',
    description: 'Hemograma completo', orderedAt: '2026-03-01T09:00:00', situation: 'OPEN', performedAt: null,
    performedByName: null, reportedAt: null, reportedByName: null, mayRead: true, ...overrides,
  };
}

const reported = (orderId: string, reportedAt: string) =>
  order({ orderId, situation: 'REPORTED', performedAt: '2026-01-01T08:00:00', reportedAt, reportedByName: 'Dr. Benítez' });

describe('the orders a practitioner issued', () => {
  it('says where each stands, never what its result says', () => {
    expect(orderSituation(order())).toBe('Abierta: nadie hizo el estudio todavía');
    expect(orderSituation(order({ situation: 'AWAITING_RESULT', performedAt: '2026-03-05T10:15:00',
      performedByName: 'Dra. Arias' }))).toBe('Realizada el jue 05/03/2026 10:15 por Dra. Arias; espera el resultado');
    expect(orderSituation(reported('o', '2026-03-09T14:02:00'))).toBe('Resultado del lun 09/03/2026 14:02, de Dr. Benítez');
    expect(orderSituation({ ...reported('o', '2026-03-09T14:02:00'), reportedByName: null }))
      .toContain('(profesional desconocido)');
  });

  /** What waits, the oldest first; what was reported, the newest first, from the last 30 days unless all (D-56). */
  it('splits what waits, the oldest first, from what was reported in the last 30 days, the newest first', () => {
    const orders = [
      order({ orderId: 'w2', orderedAt: '2026-03-05T09:00:00', situation: 'AWAITING_RESULT' }),
      order({ orderId: 'w1', orderedAt: '2026-02-01T09:00:00' }),
      reported('r1', '2026-02-08T00:00:00'),
      reported('r2', '2026-03-09T10:00:00'),
      reported('old', '2026-02-07T23:59:00'),
    ];
    const recent = splitOrders(orders, '2026-03-10', false);
    expect(recent.waiting.map((each) => each.orderId)).toEqual(['w1', 'w2']);
    expect(recent.reported.map((each) => each.orderId)).toEqual(['r2', 'r1']);
    expect(splitOrders(orders, '2026-03-10', true).reported.map((each) => each.orderId)).toEqual(['r2', 'r1', 'old']);
  });

  it('names a patient with their document, or says them unknown', () => {
    expect(patientOf(order())).toBe('Ana Pérez — DNI 30111222');
    expect(patientOf(order({ patientDocument: null }))).toBe('Ana Pérez');
    expect(patientOf(order({ patientName: null }))).toBe('(paciente desconocido)');
  });
});

describe('asking for access to a record', () => {
  it('says the reason is optional only where the practice does not require it', () => {
    expect(reasonLabel(true)).toBe('Motivo');
    expect(reasonLabel(false)).toBe('Motivo (opcional)');
    expect(reasonLabel(undefined)).toBe('Motivo');
  });

  it('says until when it was granted, and each refusal', () => {
    expect(grantedUntil('2027-03-10')).toBe('Acceso autorizado hasta el 10/03/2027.');
    const refused = (status: number, code?: string) =>
      new ApiError(status, { status, error: 'x', message: 'x', ...(code ? { code } : {}) });
    expect(describeError(refused(400, 'REASON_REQUIRED'), CHECK_ACCESS_REFUSALS)).toBe('Escribí el motivo del pedido de acceso.');
    expect(describeError(refused(409, 'CHECK_NOT_PENDING'), CHECK_ACCESS_REFUSALS))
      .toBe('Ese estudio ya tiene resultado: no hace falta informarlo.');
    expect(describeError(refused(400), CHECK_ACCESS_REFUSALS)).toBe('El motivo tiene hasta 500 caracteres.');
  });
});
