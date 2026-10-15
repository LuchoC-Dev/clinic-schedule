import type { Attachment, Check, ClinicalRecord, Consultation, Order } from '../staff/record/record';

/** A check as the API answers it: pending, done by Dra. Arias. */
export function check(overrides: Partial<Check> = {}): Check {
  return {
    id: 'k1', patientId: 'p1', performedBy: 'd1', performedByName: 'Dra. Arias', performedAt: '2026-03-10T10:15:00',
    description: 'Extracción de sangre', consultationId: null, pending: true, result: null, addenda: [], ...overrides,
  };
}

export function order(overrides: Partial<Order> = {}): Order {
  return {
    id: 'o1', patientId: 'p1', orderedBy: 'd1', orderedByName: 'Dra. Arias', orderedAt: '2026-03-02T09:00:00',
    consultationId: 'c1', description: 'Ecodoppler de vasos de cuello', status: 'OPEN', fulfilledBy: null,
    cancelledAt: null, cancellationReason: null, ...overrides,
  };
}

export function consultation(overrides: Partial<Consultation> = {}): Consultation {
  return {
    id: 'c1', patientId: 'p1', practitionerId: 'd1', practitionerName: 'Dra. Arias', recordedAt: '2026-03-02T09:00:00',
    notes: 'Control de presión.', items: [], checks: [], orders: [], addenda: [], ...overrides,
  };
}

export function file(overrides: Partial<Attachment> = {}): Attachment {
  return {
    id: 'f1', owner: 'RECORD', consultationId: null, checkId: null, fileName: 'historia.pdf',
    mediaType: 'application/pdf', sizeBytes: 120 * 1024, uploadedBy: 'd1', uploadedByName: 'Dra. Arias',
    uploadedAt: '2026-01-10T12:00:00', ...overrides,
  };
}

export function clinicalRecord(overrides: Partial<ClinicalRecord> = {}): ClinicalRecord {
  return {
    patient: {
      fullName: 'Tomás Suárez', document: 'DNI 30123456', identityVerified: true, birthDate: '1980-01-15',
      insurance: { insurer: 'OSDE', memberNumber: null, plan: '310' },
    },
    mayRecord: false, consultations: [], checks: [], attachments: [], accessGrants: [], ...overrides,
  };
}
