import { when } from '../../shared/dates';
import type { Cancellation } from '../agenda/agenda';
import { REQUESTER } from '../words';

/**
 * Who cancelled an appointment, when, how and who asked (D-76, D-83), as the Thymeleaf screens said it: the
 * practitioner's absence, the patient on the web, the email's link, or reception by hand.
 */
export function cancellationText(cancellation: Cancellation | null): string {
  if (!cancellation || !cancellation.at) {
    return 'Sin registro de la cancelación: es anterior a que se registraran.';
  }
  const at = when(cancellation.at);
  const asked = cancellation.requestedBy ? ` · lo pidió ${REQUESTER[cancellation.requestedBy]}` : '';
  switch (cancellation.origin) {
    case 'TIME_OFF':
      return `Por la ausencia del profesional, cargada por ${cancellation.by} el ${at}`;
    case 'ONLINE':
      return `Por el paciente, en la web, el ${at}`;
    case 'EMAIL_LINK':
      return `Con el link del email, el ${at}${asked}`;
    default:
      return `Por ${cancellation.by} el ${at}${asked}`;
  }
}
