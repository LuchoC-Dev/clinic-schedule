import type { RecordCopy } from '../staff/copies/copies';

/** A copy as the API answers it: the patient's own, a PDF, waiting, 31 hours left. */
export function recordCopy(overrides: Partial<RecordCopy> = {}): RecordCopy {
  return {
    id: 'r1', patient: { id: 'p1', fullName: 'Ana Pérez', document: 'DNI 30111222' },
    requestedBy: { id: 'p1', fullName: 'Ana Pérez', document: 'DNI 30111222' }, requestedAt: '2026-03-10T09:30:00',
    delivery: 'PDF', code: 'K7QM-3XPA-9TRD', assembledAt: '2026-03-10T09:30:00', assembly: 1,
    deadline: '2026-03-12T09:30:00', minutesLeft: 31 * 60, late: false, authentication: null, handedOverAt: null,
    mayRead: true, ...overrides,
  };
}
