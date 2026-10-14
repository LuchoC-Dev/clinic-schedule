import { describe, expect, it } from 'vitest';

import { ApiError, NetworkError } from './api';
import { describeError } from './errors';

const refused = (status: number, message = 'whatever') => new ApiError(status, { status, error: 'x', message });

describe('what a refusal says', () => {
  it("says a screen's own refusal in its words, from the server's body", () => {
    const words = (error: ApiError) =>
      error.body?.message.includes('already has an appointment') ? 'El profesional ya tiene un turno en ese horario.' : undefined;

    expect(describeError(refused(409, 'The practitioner already has an appointment then'), words))
      .toBe('El profesional ya tiene un turno en ese horario.');
  });

  it('falls back to a sentence by status for what the screen does not know, never in English', () => {
    expect(describeError(refused(409))).toBe('No se pudo: el estado cambió mientras tanto. Revisá y probá de nuevo.');
    expect(describeError(refused(403))).toBe('No tenés permiso para hacer esto.');
    expect(describeError(refused(400))).toBe('Revisá los datos: hay algo que no es válido.');
    expect(describeError(refused(404))).toBe('No se encontró lo que buscabas: puede que ya no exista.');
    expect(describeError(refused(401))).toBe('Tu sesión terminó: ingresá de nuevo.');
    expect(describeError(refused(503))).toBe('Algo falló en el servidor. Probá de nuevo en un momento.');
    expect(describeError(refused(409, 'Some English the server said'))).not.toContain('English');
  });

  it('says when the server could not be reached', () => {
    expect(describeError(new NetworkError(new TypeError('down'))))
      .toBe('No se pudo conectar con el servidor. Revisá la conexión y probá de nuevo.');
  });
});
