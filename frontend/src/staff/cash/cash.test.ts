import { describe, expect, it } from 'vitest';

import { cancelledBy, reminderOutcome } from '../words';
import { cashPatient, coveredInsurance, type Movement, movementKind, signedAmount, type Total, tiles } from './cash';

function movement(overrides: Partial<Movement> = {}): Movement {
  return {
    kind: 'CHARGE', visitId: 'v1', patient: { id: 'p1', fullName: 'Ana Pérez', document: 'DNI 30111222' },
    emergency: false, copay: false, amount: 15000, means: 'CASH', left: 15000, reason: null, recordedBy: 'recepcion',
    recordedAt: '2026-03-10T10:15:00', refundable: true, chargeableAgain: false, ...overrides,
  };
}

const sum = (count: number, amount: number) => ({ count, amount });

describe("the cash desk's day", () => {
  it('names a patient with their document, or says them unknown', () => {
    expect(cashPatient(movement().patient)).toBe('Ana Pérez — DNI 30111222');
    expect(cashPatient({ id: 'p', fullName: 'NN-4821', document: null })).toBe('NN-4821');
    expect(cashPatient({ id: 'p', fullName: null, document: null })).toBe('(paciente desconocido)');
  });

  /** A copay is a charge as any other, said apart (D-90); a refund goes out of the drawer, with a minus (D-88). */
  it('says each movement and its amount', () => {
    expect(movementKind(movement())).toBe('Cobro');
    expect(movementKind(movement({ copay: true }))).toBe('Coseguro');
    expect(movementKind(movement({ kind: 'REFUND', copay: true }))).toBe('Devolución');
    expect(signedAmount(movement())).toBe('$ 15.000,00');
    expect(signedAmount(movement({ kind: 'REFUND', amount: 2500.5 }))).toBe('−$ 2.500,50');
  });

  it('says a covered visit’s insurance with what it had', () => {
    const covered = { visitId: 'v', patient: movement().patient, emergency: false, insurer: 'OSDE', memberNumber: '61234',
      plan: '310', token: null, recordedBy: 'r', recordedAt: '', withdrawal: null, chargeable: false };
    expect(coveredInsurance(covered)).toBe('OSDE · plan 310 · afiliado 61234');
    expect(coveredInsurance({ ...covered, plan: null, memberNumber: null })).toBe('OSDE');
    expect(coveredInsurance({ ...covered, memberNumber: null })).toBe('OSDE · plan 310');
    expect(coveredInsurance({ ...covered, plan: null })).toBe('OSDE · afiliado 61234');
  });

  /** Only cash counts for the drawer (D-89); the other means are said apart, and what went back. */
  it('sums up the day in tiles: the drawer, each other means used, and the refunds', () => {
    const total: Total = {
      charged: sum(4, 80000), refunded: sum(1, 12000), left: 68000, drawer: 30000,
      byMeans: [
        { means: 'CASH', charged: sum(2, 42000), refunded: sum(1, 12000), left: 30000 },
        { means: 'DEBIT', charged: sum(2, 38000), refunded: sum(0, 0), left: 38000 },
        { means: 'QR', charged: sum(0, 0), refunded: sum(0, 0), left: 0 },
      ],
    };
    expect(tiles(total)).toEqual([
      { label: 'Efectivo en caja', amount: '$ 30.000,00' },
      { label: 'Débito', amount: '$ 38.000,00' },
      { label: 'Devoluciones', amount: '−$ 12.000,00' },
    ]);
    expect(tiles({ ...total, refunded: sum(0, 0), byMeans: [] }).at(-1)).toEqual({ label: 'Devoluciones', amount: '$ 0,00' });
  });
});

describe('the logs', () => {
  /** Who cancelled it, as reception reads it (D-76, D-83). */
  it('say who cancelled an appointment', () => {
    expect(cancelledBy('BY_HAND', 'recepcion')).toBe('recepcion');
    expect(cancelledBy('TIME_OFF', 'admin')).toBe('admin');
    expect(cancelledBy('ONLINE', 'portal:x')).toBe('el paciente (web)');
    expect(cancelledBy('EMAIL_LINK', null)).toBe('el link del email');
    expect(cancelledBy('BY_HAND', null)).toBe('—');
  });

  /** What happened to a reminder, never the address (D-78). */
  it('say what happened to a reminder', () => {
    expect(reminderOutcome('SENT', null)).toBe('Enviado por email');
    expect(reminderOutcome('FAILED', null)).toBe('No se pudo enviar: avisale por teléfono');
    expect(reminderOutcome('NOT_SENT', 'NO_EMAIL')).toBe('No enviado: no tiene email');
    expect(reminderOutcome('NOT_SENT', 'NO_CONSENT')).toBe('No enviado: sin consentimiento para el email');
    expect(reminderOutcome('NOT_SENT', 'CONFIDENTIAL')).toContain('es de un responsable');
  });
});
