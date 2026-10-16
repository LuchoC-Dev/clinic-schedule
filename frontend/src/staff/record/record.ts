import { when } from '../../shared/dates';

/**
 * What a practitioner's screens decide over a clinical record (D-104): the record as the API answers it (D-51, D-53,
 * D-55, D-57, D-94), put together as the Thymeleaf record showed it — every entry the newest first, each check with
 * where it stands, each order with how it ended —, and who the patient is beside it. The rules stay in the server.
 */

export interface Addendum {
  id: string;
  practitionerId: string;
  practitionerName: string | null;
  writtenAt: string;
  text: string;
}

export interface Check {
  id: string;
  patientId: string;
  performedBy: string;
  performedByName: string | null;
  performedAt: string;
  description: string;
  consultationId: string | null;
  pending: boolean;
  result: { authorId: string; authorName: string | null; writtenAt: string; text: string | null } | null;
  addenda: Addendum[];
}

export type OrderStatus = 'OPEN' | 'FULFILLED' | 'CANCELLED';

export interface Order {
  id: string;
  patientId: string;
  orderedBy: string;
  orderedByName: string | null;
  orderedAt: string;
  consultationId: string;
  description: string;
  status: OrderStatus;
  fulfilledBy: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
}

export interface Consultation {
  id: string;
  patientId: string;
  practitionerId: string;
  practitionerName: string | null;
  recordedAt: string;
  notes: string;
  items: { kind: 'PRESCRIPTION'; description: string }[];
  checks: Check[];
  orders: Order[];
  addenda: Addendum[];
}

export interface Attachment {
  id: string;
  owner: 'CONSULTATION' | 'CHECK' | 'RECORD';
  consultationId: string | null;
  checkId: string | null;
  fileName: string;
  mediaType: string;
  sizeBytes: number;
  uploadedBy: string;
  uploadedByName: string | null;
  uploadedAt: string;
}

export interface AccessGrant {
  id: string;
  practitionerId: string;
  practitionerName: string | null;
  checkId: string | null;
  recordCopyId: string | null;
  grantedAt: string;
  validUntil: string;
  reason: string | null;
}

export interface ClinicalRecord {
  patient: {
    fullName: string | null;
    document: string | null;
    identityVerified: boolean;
    birthDate: string | null;
    insurance: { insurer: string; memberNumber: string | null; plan: string | null } | null;
  };
  /** The patient arrived to be seen by this practitioner today: a consultation or a check would be accepted. */
  mayRecord: boolean;
  consultations: Consultation[];
  /** The checks done without a consultation; those done in one come with it. */
  checks: Check[];
  attachments: Attachment[];
  accessGrants: AccessGrant[];
}

/** `(profesional desconocido)` for a name the practitioner module no longer knows, as on the screens. */
export function nameOf(name: string | null): string {
  return name ?? '(profesional desconocido)';
}

/**
 * The patient's age as a practitioner reads it beside the record (D-73): in years, and in months below two, as
 * paediatrics counts it — in days for a baby not a month old. Null without a date of birth.
 */
export function ageOn(birthDate: string | null, today: string): string | null {
  if (!birthDate) {
    return null;
  }
  const [by, bm, bd] = birthDate.split('-').map(Number);
  const [ty, tm, td] = today.split('-').map(Number);
  let months = (ty - by) * 12 + (tm - bm);
  if (td < bd) {
    months -= 1;
  }
  if (months >= 24) {
    return `${Math.floor(months / 12)} años`;
  }
  if (months >= 1) {
    return months === 1 ? '1 mes' : `${months} meses`;
  }
  const days = Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(by, bm - 1, bd)) / 86_400_000);
  return days === 1 ? '1 día' : `${days} días`;
}

/**
 * Who the patient is, in one line under their name: the document, the age, the insurance — or that none is on the
 * books, which does not mean they pay privately (D-74) — and whether reception saw the document (D-60).
 */
export function patientLine(patient: ClinicalRecord['patient'], today: string): string {
  const insurance = patient.insurance;
  return [
    patient.document,
    ageOn(patient.birthDate, today),
    insurance
      ? [insurance.insurer, insurance.plan && `plan ${insurance.plan}`, insurance.memberNumber
        && `afiliado ${insurance.memberNumber}`].filter(Boolean).join(' · ')
      : 'sin obra social cargada',
    patient.identityVerified ? 'identidad verificada' : 'identidad no verificada',
  ].filter(Boolean).join(' · ');
}

/** One entry of the record's history: a consultation, a check done without one, or a file of the record as a whole. */
export type Entry =
  | { kind: 'consultation'; at: string; consultation: Consultation }
  | { kind: 'check'; at: string; check: Check }
  | { kind: 'file'; at: string; file: Attachment };

/** The record's history, everything mixed by time, the newest first (D-53, D-94). */
export function historyOf(record: ClinicalRecord): Entry[] {
  const entries: Entry[] = [
    ...record.consultations.map((consultation): Entry =>
      ({ kind: 'consultation', at: consultation.recordedAt, consultation })),
    ...record.checks.map((check): Entry => ({ kind: 'check', at: check.performedAt, check })),
    ...filesOf(record, 'RECORD').map((file): Entry => ({ kind: 'file', at: file.uploadedAt, file })),
  ];
  return entries.sort((a, b) => b.at.localeCompare(a.at));
}

/** Every check, done in a consultation or not, the newest first. */
export function allChecks(record: ClinicalRecord): Check[] {
  return [...record.consultations.flatMap((each) => each.checks), ...record.checks]
    .sort((a, b) => b.performedAt.localeCompare(a.performedAt));
}

/** Every order, the newest first. */
export function allOrders(record: ClinicalRecord): Order[] {
  return record.consultations.flatMap((each) => each.orders)
    .sort((a, b) => b.orderedAt.localeCompare(a.orderedAt));
}

/** The orders a check done now may fulfil (D-55), the oldest first, as the screens list them. */
export function openOrders(record: ClinicalRecord): Order[] {
  return allOrders(record).filter((order) => order.status === 'OPEN').reverse();
}

/** The files of a consultation, of a check, or of the record as a whole, in the order they were uploaded. */
export function filesOf(record: ClinicalRecord, owner: Attachment['owner'], id?: string): Attachment[] {
  return record.attachments
    .filter((file) => file.owner === owner
      && (owner === 'RECORD' || (owner === 'CONSULTATION' ? file.consultationId : file.checkId) === id))
    .sort((a, b) => a.uploadedAt.localeCompare(b.uploadedAt));
}

/** Where a check stands: its result, in words or in its file, with who and when; or pending. */
export function resultOf(check: Check): { pending: boolean; text: string; by: string | null } {
  if (!check.result) {
    return { pending: true, text: 'Resultado pendiente.', by: null };
  }
  return {
    pending: false,
    text: check.result.text ?? 'en el archivo adjunto',
    by: `${nameOf(check.result.authorName)}, ${when(check.result.writtenAt)}`,
  };
}

/** Where an order stands, as the Thymeleaf record said it: open, fulfilled on the day its check was done, or cancelled. */
export function orderState(order: Order, checks: Check[]): string {
  if (order.status === 'CANCELLED') {
    return `Anulada: ${order.cancellationReason ?? ''}`;
  }
  if (order.status === 'FULFILLED') {
    const check = checks.find((each) => each.id === order.fulfilledBy);
    return check ? `Cumplida el ${when(check.performedAt)}` : 'Cumplida';
  }
  return 'Abierta';
}

/** Only its author cancels an order, while it is open (D-55). */
export function mayCancel(order: Order, practitionerId: string | null): boolean {
  return order.status === 'OPEN' && order.orderedBy === practitionerId;
}

/** `120 KB`, `1,5 MB`. */
export function sizeOf(bytes: number): string {
  return bytes < 1024 * 1024
    ? `${Math.max(1, Math.floor(bytes / 1024))} KB`
    : `${(bytes / (1024 * 1024)).toLocaleString('es-AR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} MB`;
}

/** What an access was granted for: a check to report (D-54), or a copy to authenticate (D-95). */
export function grantedFor(grant: AccessGrant, checks: Check[]): string {
  if (grant.recordCopyId) {
    return 'Autenticar una copia de la historia';
  }
  return checks.find((each) => each.id === grant.checkId)?.description ?? '';
}
