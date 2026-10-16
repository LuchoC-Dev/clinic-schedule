import { describe, expect, it } from 'vitest';

import { ApiError } from '../shared/api';
import { describeError } from '../shared/errors';
import { type Copy, copyRow } from './copies/copies';
import {
  birthDateSaid, consentChoices, consentValue, detailsBody, detailsFormOf, type Profile,
} from './profile/profile';
import { CHILD_REFUSALS, childAsked, type Group, memberRow, sharesMine } from './family/family';

const refusal = (status: number, code?: string) =>
  new ApiError(status, { status, error: 'Refused', message: 'in English', code });

describe('a family group', () => {
  it('says each member by name, age band and sharing, the patient as "vos", a child always sharing', () => {
    expect(memberRow({ fullName: 'Laura Medina', age: 'ADULT', shares: false, me: true }))
      .toEqual({ name: 'Laura Medina (vos)', age: 'adulto', sharing: 'Turnos privados' });
    expect(memberRow({ fullName: 'Benjamín', age: 'CHILD', shares: false, me: false }))
      .toEqual({ name: 'Benjamín', age: 'menor de 13', sharing: 'Los comparte siempre (menor de 13)' });
    expect(memberRow({ fullName: 'Lola', age: 'ADOLESCENT', shares: true, me: false }))
      .toEqual({ name: 'Lola', age: 'de 13 a 17', sharing: 'Comparte sus turnos' });
    expect(memberRow({ fullName: 'Sin fecha', age: null, shares: true, me: false }).age).toBe('');
  });

  it("knows whether the patient shares their own", () => {
    const group: Group = { id: 'g', name: 'Familia', members: [
      { fullName: 'Otra', age: 'ADULT', shares: true, me: false },
      { fullName: 'Yo', age: 'ADULT', shares: false, me: true }] };
    expect(sharesMine(group)).toBe(false);
    expect(sharesMine({ ...group, members: [{ ...group.members[1], shares: true }] })).toBe(true);
  });

  it("says registering a child refused in the Thymeleaf screen's words, the same whatever the document", () => {
    expect(describeError(refusal(400, 'INVALID_REGISTRATION'), CHILD_REFUSALS)).toContain('Revisá sus datos');
    expect(describeError(refusal(404, 'FAMILY_GROUP_NOT_FOUND'), CHILD_REFUSALS)).toBe('Ese grupo ya no está.');
    expect(describeError(refusal(400, 'ILLEGAL_ARGUMENT'), CHILD_REFUSALS)).toBe('Falta su fecha de nacimiento.');
    expect(describeError(refusal(429), CHILD_REFUSALS)).toContain('Por hoy ya pediste registrar a varios hijos');
    expect(describeError(refusal(409, 'NOT_A_CHILD'), CHILD_REFUSALS)).toContain('tus hijos menores de 13');
    expect(childAsked(' Tomás ')).toBe('Recibimos el pedido. Si Tomás no aparece en tu familia, acercate a recepción '
      + 'con su documento.');
  });
});

const PROFILE: Profile = {
  fullName: 'Laura Medina', document: 'DNI 27333444', birthDate: '1990-05-04', ageBand: 'ADULT',
  phone: { value: '1155667788', owner: { name: 'Marta', relation: 'MOTHER' }, consent: 'OWNER' },
  email: null, insurance: { insurer: 'OSDE', memberNumber: null, plan: '210' },
};

describe('the profile', () => {
  it('starts the form from what is kept, an empty field for what is not', () => {
    expect(detailsFormOf(PROFILE)).toEqual({
      phoneNumber: '1155667788', phoneOwnerRelation: 'MOTHER', phoneOwnerName: 'Marta', emailAddress: '',
      emailOwnerRelation: '', emailOwnerName: '', insurer: 'OSDE', memberNumber: '', plan: '210',
    });
  });

  it('sends the form as typed, an owner only when chosen or named', () => {
    const form = detailsFormOf(PROFILE);
    expect(detailsBody(form)).toEqual({
      phone: { number: '1155667788', owner: { name: 'Marta', relation: 'MOTHER' } },
      email: { address: '', owner: null },
      insurance: { insurer: 'OSDE', memberNumber: '', plan: '210' },
    });
    expect(detailsBody({ ...form, phoneOwnerRelation: '', phoneOwnerName: 'Marta' }).phone.owner)
      .toEqual({ name: 'Marta', relation: '' });
  });

  it("offers first the consent that stands, so saving untouched never agrees", () => {
    expect(consentChoices(PROFILE.phone).map((choice) => choice.value)).toEqual(['', 'true', 'false']);
    expect(consentChoices(PROFILE.phone)[0].label).toBe('Sí: lo autorizó Marta');
    expect(consentChoices({ value: 'a@b.com', owner: null, consent: null }).map((choice) => choice.value))
      .toEqual(['false', 'true']);
    expect(consentChoices({ value: 'a@b.com', owner: null, consent: 'PATIENT' }).map((choice) => choice.value))
      .toEqual(['true', 'false']);
    expect(consentChoices(null)).toEqual([]);
  });

  it('sends a choice as the API takes it, the owner\'s left as it is', () => {
    expect(consentValue('true')).toBe(true);
    expect(consentValue('false')).toBe(false);
    expect(consentValue('')).toBeNull();
    expect(consentValue(undefined)).toBeNull();
  });

  it('says the date of birth as the Thymeleaf screen did', () => {
    expect(birthDateSaid('1990-05-04')).toBe('4/05/1990');
  });
});

function copy(changes: Partial<Copy>): Copy {
  return {
    id: 'c1', patient: { id: 'me', fullName: 'Laura Medina' }, requestedAt: '2026-03-09T09:30:00', delivery: 'PDF',
    state: 'WAITING', deadline: '2026-03-11T09:30:00', authenticatedAt: null, handedOverAt: null, own: true, ...changes,
  };
}

describe('a copy of the record', () => {
  it('says whose it is, when it was asked, how it is delivered and where it stands', () => {
    expect(copyRow(copy({}))).toEqual({
      whose: 'Tu historia', requestedAt: '09/03/2026 09:30', delivery: 'PDF',
      state: 'Esperando que un profesional la autentique (a más tardar el 11/03/2026 09:30)', downloadable: false,
    });
    expect(copyRow(copy({ own: false, patient: { id: 'k', fullName: 'Benjamín Medina' } })).whose)
      .toBe('Historia de Benjamín Medina');
    expect(copyRow(copy({ state: 'READY_TO_DOWNLOAD' }))).toMatchObject({ state: 'Lista para descargar', downloadable: true });
    expect(copyRow(copy({ state: 'READY_TO_COLLECT', delivery: 'PAPER' })))
      .toMatchObject({ delivery: 'Papel', state: 'Lista para retirar en recepción, con tu documento', downloadable: false });
    expect(copyRow(copy({ state: 'HANDED_OVER', handedOverAt: '2026-03-12T11:05:00' })).state)
      .toBe('Entregada el 12/03/2026 11:05');
  });
});
