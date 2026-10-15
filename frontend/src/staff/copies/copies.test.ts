import { describe, expect, it } from 'vitest';

import { ApiError } from '../../shared/api';
import { describeError } from '../../shared/errors';
import { recordCopy } from '../../test/copies';
import { AUTHENTICATION_REFUSALS, REASSEMBLY_REFUSALS, askedBy, authenticatedNotice, deliveryOf, personOf,
  timeLeft } from './copies';

describe('a copy of a record', () => {
  it('says how long is left of the 48 hours, or how late it is', () => {
    expect(timeLeft(31 * 60 + 59)).toBe('Quedan 31 h');
    expect(timeLeft(60)).toBe('Quedan 1 h');
    expect(timeLeft(40)).toBe('Quedan 40 min');
    expect(timeLeft(0)).toBe('Quedan 1 min');
    expect(timeLeft(-3 * 60 - 10)).toBe('Vencida hace 3 h');
    expect(timeLeft(-5)).toBe('Vencida hace 5 min');
  });

  it('says who asked — the patient, or their parent — and how it is delivered', () => {
    expect(askedBy(recordCopy())).toBe('El paciente');
    expect(askedBy(recordCopy({ requestedBy: { id: 'm1', fullName: 'Rosa Pérez', document: null } }))).toBe('Rosa Pérez');
    expect(askedBy(recordCopy({ requestedBy: { id: 'm1', fullName: null, document: null } }))).toBe('(desconocido)');
    expect(deliveryOf(recordCopy())).toBe('PDF en su cuenta');
    expect(deliveryOf(recordCopy({ delivery: 'PAPER' }))).toBe('Papel, en recepción');
    expect(personOf(recordCopy().patient)).toBe('Ana Pérez — DNI 30111222');
    expect(personOf({ id: 'x', fullName: null, document: null })).toBe('(paciente desconocido)');
  });

  it('says what to do once it is authenticated: the paper to reception, or the patient downloads it', () => {
    expect(authenticatedNotice(recordCopy({ delivery: 'PAPER' }))).toContain('dejala en recepción en un sobre cerrado');
    expect(authenticatedNotice(recordCopy())).toBe('Copia autenticada. El paciente ya puede descargarla desde su cuenta.');
  });

  it('says why it was refused', () => {
    const refused = (code: string) => new ApiError(409, { status: 409, error: 'x', message: 'x', code });
    expect(describeError(refused('NOT_REVIEWED'), AUTHENTICATION_REFUSALS))
      .toBe('Marcá que revisaste la copia antes de autenticarla.');
    expect(describeError(refused('REASSEMBLED_SINCE'), AUTHENTICATION_REFUSALS))
      .toBe('La copia se armó de nuevo después de que la abriste: revisala otra vez.');
    expect(describeError(refused('ALREADY_AUTHENTICATED'), REASSEMBLY_REFUSALS))
      .toBe('La copia ya está autenticada: no se arma de nuevo.');
  });
});
