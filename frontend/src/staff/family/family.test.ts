import { describe, expect, it } from 'vitest';

import { accountActions, sentSaid } from '../account/account';
import { accountChangeBy } from '../words';
import { type Member, memberView, namesOf } from './family';

function member(overrides: Partial<Member> = {}): Member {
  return { patientId: 'p1', fullName: 'Marta Gómez', ageBand: 'ADULT', shares: true, requestedBy: null, relation: null,
    relationProved: true, ...overrides };
}

/** How a member joins and whether they decide is their age's (D-84). */
describe("a family group's member", () => {
  it('is an adult who decides, and shares or not', () => {
    expect(memberView(member(), {})).toEqual({ age: 'adulto', sharing: 'Comparte sus turnos', requestedBy: null,
      decides: true, adult: true });
    expect(memberView(member({ shares: false }), {}).sharing).toBe('Turnos privados');
  });

  it('decides from 13 without being an adult', () => {
    expect(memberView(member({ ageBand: 'ADOLESCENT', shares: false }), {}))
      .toMatchObject({ age: 'de 13 a 17', sharing: 'Turnos privados', decides: true, adult: false });
  });

  it('shares always under 13, decides nothing, and says who asked for them', () => {
    expect(memberView(member({ ageBand: 'CHILD', shares: false, requestedBy: 'p0', relation: 'GUARDIAN' }),
      { p0: 'Luis Gómez' })).toEqual({ age: 'menor de 13', sharing: 'Siempre (menor de 13)',
      requestedBy: 'A pedido de Luis Gómez (tutor o tutora)', decides: false, adult: false });
  });

  it('without a date of birth, says so and decides nothing', () => {
    expect(memberView(member({ ageBand: null }), {})).toMatchObject({ age: 'sin fecha de nacimiento', decides: false,
      adult: false });
  });

  it('is named by the members, or by the history when they left', () => {
    expect(namesOf([member()], [{ action: 'REMOVED', patientId: 'p0', patient: 'Luis Gómez', at: 'x', by: 'r' },
      { action: 'CREATED', patientId: null, patient: null, at: 'x', by: 'r' }])).toEqual({ p1: 'Marta Gómez', p0: 'Luis Gómez' });
  });
});

/** What reception may do with an account, and what it says afterwards (D-79, D-86). */
describe("a patient's web account", () => {
  it('offers sending the activation again while pending, and switching it off unless it is off', () => {
    expect(accountActions('PENDING')).toEqual({ resend: true, deactivate: true, reactivate: false });
    expect(accountActions('ACTIVE')).toEqual({ resend: false, deactivate: true, reactivate: false });
    expect(accountActions('INACTIVE')).toEqual({ resend: false, deactivate: false, reactivate: true });
  });

  it('says whether the activation went out, and how a reactivated one stands', () => {
    const account = { id: 'x', patientId: 'p', email: 'ana@mail.com', status: 'PENDING' as const, openedAt: '' };
    expect(sentSaid('', { ...account, activationSent: true })).toBe('Cuenta abierta: le enviamos el link de activación a ana@mail.com.');
    expect(sentSaid('', { ...account, activationSent: false })).toBe('Cuenta abierta, pero el email de activación no salió: reenvialo.');
    expect(sentSaid('/x/activation-resend', { ...account, activationSent: false }))
      .toBe('El email de activación no salió: probá de nuevo más tarde.');
    expect(sentSaid('/x/email-change', { ...account, status: 'ACTIVE' })).toBe('Email de la cuenta: ana@mail.com.');
    expect(sentSaid('/x/email-change', { ...account, activationSent: true }))
      .toBe('Email cambiado: le enviamos el link de activación a ana@mail.com.');
    expect(sentSaid('/x/deactivate', account)).toBe('Cuenta desactivada: ya no puede ingresar.');
    expect(sentSaid('/x/reactivate', { ...account, status: 'ACTIVE' })).toBe('Cuenta reactivada: puede ingresar con su contraseña.');
    expect(sentSaid('/x/reactivate', account)).toBe('Cuenta reactivada: nunca se activó, así que reenviale el link de activación.');
  });

  it("says who did something: reception's user, the patient, or the web", () => {
    expect(accountChangeBy('OPENED', 'recepcion')).toBe('recepcion');
    expect(accountChangeBy('ACTIVATED', null)).toBe('el paciente');
    expect(accountChangeBy('RECOVERY_SENT', null)).toBe('pedido desde la web');
    expect(accountChangeBy('LOCKED', null)).toBe('');
  });
});
