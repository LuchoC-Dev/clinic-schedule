import { shortDay, timeOf } from '../../shared/dates';
import { byCode } from '../../shared/refusals';

/**
 * What *Mi historia clínica* decides (D-95, D-96): each copy asked for as the patient follows it — whose, when, how it
 * is delivered and where it stands —, never anything of the record, nor who authenticated it.
 */

export type Delivery = 'PDF' | 'PAPER';
export type State = 'WAITING' | 'READY_TO_DOWNLOAD' | 'READY_TO_COLLECT' | 'HANDED_OVER';

export interface Copy {
  id: string;
  patient: { id: string; fullName: string | null };
  requestedAt: string;
  delivery: Delivery;
  state: State;
  deadline: string;
  authenticatedAt: string | null;
  handedOverAt: string | null;
  /** Of the patient's own record (D-112). */
  own: boolean;
}

export interface Copies {
  /** Whose record the patient may ask a copy of: themselves first, when they may, then their children. */
  askableFor: { id: string; fullName: string }[];
  copies: Copy[];
}

const at = (dateTime: string) => `${shortDay(dateTime)} ${timeOf(dateTime)}`;

/** A copy as the Thymeleaf screen said it. */
export function copyRow(copy: Copy) {
  return {
    whose: copy.own ? 'Tu historia' : `Historia de ${copy.patient.fullName ?? ''}`,
    requestedAt: at(copy.requestedAt),
    delivery: copy.delivery === 'PDF' ? 'PDF' : 'Papel',
    state: stateSaid(copy),
    downloadable: copy.state === 'READY_TO_DOWNLOAD',
  };
}

function stateSaid(copy: Copy): string {
  switch (copy.state) {
    case 'WAITING':
      return `Esperando que un profesional la autentique (a más tardar el ${at(copy.deadline)})`;
    case 'READY_TO_DOWNLOAD':
      return 'Lista para descargar';
    case 'READY_TO_COLLECT':
      return 'Lista para retirar en recepción, con tu documento';
    case 'HANDED_OVER':
      return `Entregada el ${copy.handedOverAt ? at(copy.handedOverAt) : ''}`;
  }
}

export const COPY_REFUSALS = byCode({
  RECORD_COPY_WAITING: 'Ya hay una copia pedida de esa historia: esperá a que esté lista.',
});

export const ASKED = 'Pedimos la copia. Un profesional la revisa y la autentica dentro de las 48 horas.';
