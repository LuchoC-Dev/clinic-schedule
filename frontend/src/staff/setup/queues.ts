import { byCode } from '../../shared/refusals';
import type { Practitioner } from '../types';

/** A queue as the API answers it (D-62): its name, its room — null when it has none — and who serves it. */
export interface Queue {
  id: string;
  name: string;
  room: string | null;
  practitionerIds: string[];
}

export const QUEUE_REFUSALS = byCode({
  QUEUE_NAME_TAKEN: 'Ya hay otra cola con ese nombre.',
  PRACTITIONER_IN_ANOTHER_QUEUE: 'Ese profesional ya atiende otra cola: quitalo de ella primero.',
  QUEUE_RULE_VIOLATION: 'No hay nada que cambiar: la cola ya está así.',
  STREAM_CHANGED: 'Alguien cambió la cola mientras tanto. Revisala y probá de nuevo.',
  UNKNOWN_PRACTITIONER: 'Ese profesional no existe.',
  QUEUE_NOT_FOUND: 'Esa cola no existe.',
  ILLEGAL_ARGUMENT: 'Revisá los datos: la cola necesita un nombre de hasta 60 caracteres, y el consultorio, hasta 20.',
});

/** A queue's name and room as they are sent: an empty room is none. */
export function queueBody(form: { name: string; room: string }) {
  return { name: form.name.trim(), room: form.room.trim() || null };
}

/** The names of whom a queue's practitioners are, an unknown one said so. */
export function servedBy(queue: Queue, practitioners: Practitioner[]): string[] {
  return queue.practitionerIds.map((id) => practitioners.find((each) => each.id === id)?.fullName ?? '(profesional desconocido)');
}

/** Who may be added to a queue: who serves none yet, since a practitioner serves one at most (D-62). */
export function available(practitioners: Practitioner[], queues: Queue[]): Practitioner[] {
  const serving = new Set(queues.flatMap((queue) => queue.practitionerIds));
  return practitioners.filter((practitioner) => !serving.has(practitioner.id));
}
