import { describe, expect, it } from 'vitest';

import { cancellationText } from './appointments';
import { detailsBody, detailsOf } from './details';
import { consentChoices, detailText, identityText, insuranceText, withOwner } from './patient';

describe("a patient's history", () => {
  it('says an identity with its document and how verified it is', () => {
    expect(identityText({ fullName: 'Ana Pérez', document: { type: 'DNI', number: '30111222' }, identity: 'VERIFIED' }))
      .toBe('Ana Pérez, DNI 30111222 — Verificada');
    expect(identityText({ fullName: 'NN-4821', document: null, identity: 'ALIAS' })).toBe('NN-4821, sin documento — Sin datos');
    expect(identityText({ fullName: 'Juan', document: { type: 'PASSPORT', number: 'AB1' }, identity: 'DECLARED' }))
      .toBe('Juan, Pasaporte AB1 — No verificada');
  });

  it("says a detail's value as the screens do", () => {
    expect(detailText('BIRTH_DATE', { value: '1990-05-14', owner: null, memberNumber: null, plan: null })).toBe('14/05/1990');
    expect(detailText('PHONE', { value: '1145556666', owner: { name: 'Ana Pérez', relation: 'MOTHER' }, memberNumber: null,
      plan: null })).toBe('1145556666 (Madre: Ana Pérez)');
    expect(detailText('EMAIL', { value: 'a@b.com', owner: null, memberNumber: null, plan: null })).toBe('a@b.com');
    expect(detailText('INSURANCE', { value: 'OSDE', owner: null, memberNumber: '61234', plan: '310' }))
      .toBe('OSDE · plan 310 · afiliado 61234');
    expect(detailText('PHONE_CONSENT', { value: 'OWNER', owner: null, memberNumber: null, plan: null }))
      .toBe('Lo dio la persona responsable');
    expect(detailText('PHONE', null)).toBe('—');
    expect(detailText('EMAIL', { value: null, owner: null, memberNumber: null, plan: null })).toBe('—');
  });

  it('says an insurance with only what it has', () => {
    expect(insuranceText({ insurer: 'OSDE', memberNumber: null, plan: null })).toBe('OSDE');
    expect(withOwner('1145556666', null)).toBe('1145556666');
  });
});

/** Who may agree to reminders on a channel (D-77). */
describe('the consent offered', () => {
  it('is none without the channel, the patient for their own, and the owner too, named, for someone else’s', () => {
    expect(consentChoices(null)).toEqual([]);
    expect(consentChoices({ number: '1', owner: null, consent: null }).map((each) => each.label))
      .toEqual(['Sin consentimiento', 'Lo dio el paciente']);
    expect(consentChoices({ number: '1', owner: { name: 'Ana Pérez', relation: 'MOTHER' }, consent: null }))
      .toEqual([{ value: '', label: 'Sin consentimiento' }, { value: 'PATIENT', label: 'Lo dio el paciente' },
        { value: 'OWNER', label: 'Lo dio Madre: Ana Pérez' }]);
  });
});

describe("a patient's details as the API takes them", () => {
  it('leaves out what is blank, and an insurance without its insurer', () => {
    expect(detailsBody({
      birthDate: '', insurer: ' ', memberNumber: '123', plan: '',
      phone: { value: '1145556666', relation: 'MOTHER', ownerName: ' Ana ' },
      email: { value: ' ', relation: '', ownerName: '' },
    })).toEqual({ birthDate: null, phone: { number: '1145556666', owner: { name: 'Ana', relation: 'MOTHER' } },
      email: null, insurance: null });
  });

  it('comes back as it was read', () => {
    const details = detailsOf({ id: 'p', fullName: 'Ana', document: null, identity: 'DECLARED', alias: null,
      birthDate: '1990-05-14', registeredVia: 'RECEPTION',
      phone: { number: '1145556666', owner: null, consent: 'PATIENT' },
      email: { address: 'a@b.com', owner: { name: 'Luis', relation: 'FATHER' }, consent: null },
      insurance: { insurer: 'OSDE', memberNumber: null, plan: '310' } });
    expect(detailsBody(details)).toEqual({ birthDate: '1990-05-14', phone: { number: '1145556666', owner: null },
      email: { address: 'a@b.com', owner: { name: 'Luis', relation: 'FATHER' } },
      insurance: { insurer: 'OSDE', memberNumber: null, plan: '310' } });
  });
});

/** Who cancelled it, when, how and who asked (D-76, D-83). */
describe('a cancellation', () => {
  it('says each way an appointment is cancelled', () => {
    expect(cancellationText({ at: '2026-09-30T10:15:00', by: 'recepcion', origin: 'BY_HAND', requestedBy: 'PATIENT',
      note: null })).toBe('Por recepcion el mié 30/09/2026 10:15 · lo pidió el paciente');
    expect(cancellationText({ at: '2026-09-30T10:15:00', by: 'recepcion', origin: 'TIME_OFF', requestedBy: null,
      note: null })).toBe('Por la ausencia del profesional, cargada por recepcion el mié 30/09/2026 10:15');
    expect(cancellationText({ at: '2026-09-30T10:15:00', by: 'portal:x', origin: 'ONLINE', requestedBy: 'PATIENT',
      note: null })).toBe('Por el paciente, en la web, el mié 30/09/2026 10:15');
    expect(cancellationText({ at: '2026-09-30T10:15:00', by: null, origin: 'EMAIL_LINK', requestedBy: 'PATIENT',
      note: null })).toBe('Con el link del email, el mié 30/09/2026 10:15 · lo pidió el paciente');
    expect(cancellationText(null)).toBe('Sin registro de la cancelación: es anterior a que se registraran.');
    expect(cancellationText({ at: null, by: null, origin: 'BY_HAND', requestedBy: null, note: null }))
      .toBe('Sin registro de la cancelación: es anterior a que se registraran.');
  });
});
