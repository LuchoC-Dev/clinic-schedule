/** What the API answers of a patient (D-26, D-60, D-73, D-74, D-77). */
export interface Owner {
  name: string;
  relation: string;
}

export interface Patient {
  id: string;
  fullName: string;
  document: { type: string; number: string } | null;
  identity: 'ALIAS' | 'DECLARED' | 'VERIFIED';
  alias: string | null;
  birthDate: string | null;
  phone: { number: string; owner: Owner | null; consent: 'PATIENT' | 'OWNER' | null } | null;
  email: { address: string; owner: Owner | null; consent: 'PATIENT' | 'OWNER' | null } | null;
  insurance: { insurer: string; memberNumber: string | null; plan: string | null } | null;
  registeredVia: 'RECEPTION' | 'WEB';
}

export interface Practitioner {
  id: string;
  fullName: string;
  reasons: { id: string; name: string; durationMinutes: number }[];
}

/** `DNI 30111222`, or nothing. */
export function documentOf(patient: Pick<Patient, 'document'>): string | null {
  return patient.document ? `${patient.document.type} ${patient.document.number}` : null;
}
