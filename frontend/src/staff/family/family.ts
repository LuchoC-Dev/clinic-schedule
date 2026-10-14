import { AGE_BANDS, CHILD_RELATIONS } from '../words';

/** A family group as reception reads it (D-84, D-92). */
export interface FamilyGroup {
  id: string;
  name: string;
  createdAt: string;
  members: Member[];
}

export interface Member {
  patientId: string;
  fullName: string;
  /** Null without a date of birth (D-102). */
  ageBand: 'CHILD' | 'ADOLESCENT' | 'ADULT' | null;
  shares: boolean;
  requestedBy: string | null;
  relation: string | null;
  relationProved: boolean;
}

export interface FamilyChange {
  action: string;
  patientId: string | null;
  patient: string | null;
  at: string;
  by: string;
}

/** Whoever the group's screen names: its members and, from its history, those who left. */
export function namesOf(members: Member[], history: FamilyChange[]): Record<string, string> {
  const names: Record<string, string> = {};
  for (const change of history) {
    if (change.patientId && change.patient) names[change.patientId] = change.patient;
  }
  for (const member of members) names[member.patientId] = member.fullName;
  return names;
}

/**
 * What the group's screen says of a member and offers for them, as the Thymeleaf screen: their age; whether the
 * group sees their appointments — always for a child under 13 —; who asked for a child; whether they decide that
 * themselves, from 13; and whether they are an adult, who may ask for a child.
 */
export function memberView(member: Member, names: Record<string, string>) {
  return {
    age: member.ageBand ? AGE_BANDS[member.ageBand] : 'sin fecha de nacimiento',
    sharing: member.ageBand === 'CHILD' ? 'Siempre (menor de 13)'
      : member.shares ? 'Comparte sus turnos' : 'Turnos privados',
    requestedBy: member.requestedBy
      ? `A pedido de ${names[member.requestedBy] ?? '(paciente desconocido)'} (${CHILD_RELATIONS[member.relation ?? ''] ?? member.relation})`
      : null,
    decides: member.ageBand === 'ADOLESCENT' || member.ageBand === 'ADULT',
    adult: member.ageBand === 'ADULT',
  };
}
