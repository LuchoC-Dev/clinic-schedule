import { useQuery } from '@tanstack/react-query';

import type { ApiError } from '../../shared/api';
import { shortDay } from '../../shared/dates';
import type { Refusals } from '../../shared/errors';
import { byCode, combine } from '../../shared/refusals';
import { api } from '../session';

/**
 * Asking for access to a record (D-54, D-95, D-105): to report a pending check, or to authenticate a copy. Granted at
 * once for now, and recorded; whether a reason is required is the practice's setting, which the screens say.
 */

/** "Motivo", or "Motivo (opcional)" where the practice does not require one; "Motivo" until it is known. */
export function reasonLabel(required: boolean | undefined): string {
  return required === false ? 'Motivo (opcional)' : 'Motivo';
}

export function useReasonLabel(): string {
  const settings = useQuery({
    queryKey: ['access-grant-settings'],
    queryFn: () => api.get<{ reasonRequired: boolean }>('/api/access-grants/settings'),
    staleTime: Infinity,
  });
  return reasonLabel(settings.data?.reasonRequired);
}

/** `Acceso autorizado hasta el 10/03/2027.` */
export function grantedUntil(validUntil: string): string {
  return `Acceso autorizado hasta el ${shortDay(validUntil)}.`;
}

/** A refusal with no code of its own: Bean Validation's, about the reason's length. */
const tooLong: Refusals = (error: ApiError) =>
  (error.status === 400 && !error.body?.code ? 'El motivo tiene hasta 500 caracteres.' : undefined);

/** Asking for access to report a check (D-54). */
export const CHECK_ACCESS_REFUSALS = combine(byCode({
  REASON_REQUIRED: 'Escribí el motivo del pedido de acceso.',
  CHECK_NOT_PENDING: 'Ese estudio ya tiene resultado: no hace falta informarlo.',
  CHECK_NOT_FOUND: 'Ese estudio no existe.',
  ILLEGAL_ARGUMENT: 'El motivo tiene hasta 500 caracteres.',
}), tooLong);
