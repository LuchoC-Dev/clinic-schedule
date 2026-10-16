import { timeOf } from '../../shared/dates';
import { byCode } from '../../shared/refusals';
import type { Entry, Session } from '../arrivals/arrivals';

/**
 * What the practitioner's queue decides (D-63, D-104): what they may do with it, and how each patient waiting is
 * said. The practitioner never chooses whom to call: the order is the queue's.
 */

/** The practitioner's own queue, as `/api/my-queue` answers it: which one, its session, and whom they called. */
export interface MyQueue {
  queueId: string;
  queueName: string;
  session: Session;
  called: Entry | null;
}

/**
 * As the Thymeleaf screen offered it: with a patient called, finishing with them or saying they did not come in;
 * otherwise calling the next, while somebody waits. A closed queue offers nothing.
 */
export function queueActions(mine: MyQueue) {
  const open = mine.session.open;
  const called = open && mine.called !== null;
  return {
    finish: called,
    didNotComeIn: called,
    callNext: open && !called,
    nobodyWaits: mine.session.waiting.length === 0,
  };
}

/** How a patient waiting is said beside their name: an emergency, their appointment's time, or without one. */
export function waitingNote(entry: Entry): string {
  const parts: string[] = [];
  if (entry.appointmentAt) {
    parts.push(`Turno ${timeOf(entry.appointmentAt)}`);
  } else {
    parts.push('Sin turno');
  }
  if (entry.reservedFor) {
    parts.push(`espera a ${entry.reservedFor}`);
  }
  return parts.join(' · ');
}

/** The queue's refusals (D-63), in the Thymeleaf screen's words. */
export const QUEUE_REFUSALS = byCode({
  NO_QUEUE_SERVED: 'No atendés ninguna cola.',
  NOBODY_WAITING: 'No hay nadie esperando.',
  NOBODY_CALLED: 'No tenés ningún paciente llamado.',
  STILL_CALLED: 'Terminá con el paciente llamado, o indicá que no ingresó, antes de llamar al siguiente.',
  NOT_SERVING: 'No atendés esta cola.',
  QUEUE_NOT_OPEN: 'La cola está cerrada.',
  STREAM_CHANGED: 'La cola cambió mientras tanto. Revisala y probá de nuevo.',
});
