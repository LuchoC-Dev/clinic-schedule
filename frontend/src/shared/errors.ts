import { ApiError, NetworkError } from './api';

/**
 * What a screen tells the person when the API says no, in Spanish (D-13). The API answers in English; each
 * screen knows its refusals and passes `refusals`, which reads the server's body — its status, `error` and
 * `message` — and says it in Spanish, as `ScreenMessages` does for the Thymeleaf screens. What it does not
 * know falls back to a sentence by status.
 *
 * A refusal never clears what was typed: the forms keep their values, so after a 409 the person sends it again
 * as it was, or changes it (D-52).
 */
export type Refusals = (error: ApiError) => string | undefined;

export function describeError(error: unknown, refusals?: Refusals): string {
  if (error instanceof NetworkError) {
    return 'No se pudo conectar con el servidor. Revisá la conexión y probá de nuevo.';
  }
  if (!(error instanceof ApiError)) {
    return 'Algo salió mal. Probá de nuevo.';
  }
  const said = refusals?.(error);
  if (said) {
    return said;
  }
  switch (error.status) {
    case 400:
      return 'Revisá los datos: hay algo que no es válido.';
    case 401:
      return 'Tu sesión terminó: ingresá de nuevo.';
    case 403:
      return 'No tenés permiso para hacer esto.';
    case 404:
      return 'No se encontró lo que buscabas: puede que ya no exista.';
    case 409:
      return 'No se pudo: el estado cambió mientras tanto. Revisá y probá de nuevo.';
    default:
      return error.status >= 500
        ? 'Algo falló en el servidor. Probá de nuevo en un momento.'
        : 'Algo salió mal. Probá de nuevo.';
  }
}
