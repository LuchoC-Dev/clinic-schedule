import type { ApiError } from '../../shared/api';
import { byCode, combine } from '../../shared/refusals';

/**
 * What *Mi familia* decides (D-85, D-92): each member as the patient reads them — their name, how old as the group
 * counts it, whether the group sees their appointments —, and each refusal in the Thymeleaf screen's words.
 */

export type AgeBand = 'CHILD' | 'ADOLESCENT' | 'ADULT';

export interface Group {
  id: string;
  name: string;
  members: { fullName: string; age: AgeBand | null; shares: boolean; me: boolean }[];
}

const AGES: Record<AgeBand, string> = { CHILD: 'menor de 13', ADOLESCENT: 'de 13 a 17', ADULT: 'adulto' };

/** A member's row: never their date of birth, nor anything else of theirs (D-85). */
export function memberRow(member: Group['members'][number]) {
  return {
    name: member.me ? `${member.fullName} (vos)` : member.fullName,
    age: member.age ? AGES[member.age] : '',
    sharing: member.age === 'CHILD' ? 'Los comparte siempre (menor de 13)'
      : member.shares ? 'Comparte sus turnos' : 'Turnos privados',
  };
}

/** Whether the patient shares their own appointments in the group. */
export function sharesMine(group: Group): boolean {
  return group.members.find((member) => member.me)?.shares ?? false;
}

/** Sharing or leaving refused: the group is not theirs any more. */
export const GONE = 'Ya no estás en ese grupo.';

export const CHILD_REFUSALS = combine(byCode({
  INVALID_REGISTRATION: 'Revisá sus datos: el nombre, el documento y la fecha de nacimiento.',
  FAMILY_GROUP_NOT_FOUND: 'Ese grupo ya no está.',
  ILLEGAL_ARGUMENT: 'Falta su fecha de nacimiento.',
}), (error: ApiError) => {
  if (error.status === 429) {
    return 'Por hoy ya pediste registrar a varios hijos: probá mañana, o acercate a recepción.';
  }
  // Any rule of the group's: a child is under 13, in a group of the adult's (D-92).
  return error.status === 409 ? 'Desde la web registrás a tus hijos menores de 13, en un grupo tuyo.' : undefined;
});

/** The same answer whether the practice knew the document or not (D-92). */
export function childAsked(fullName: string): string {
  return `Recibimos el pedido. Si ${fullName.trim()} no aparece en tu familia, acercate a recepción con su documento.`;
}
