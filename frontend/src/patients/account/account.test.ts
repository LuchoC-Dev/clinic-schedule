import { describe, expect, it } from 'vitest';

import { ApiError } from '../../shared/api';
import { describeError } from '../../shared/errors';
import { dayAndTime } from '../words';
import {
  CHILD_SLOTS, emptyRegistration, malformed, notRegisteredSaid, registrationBody, registrationRefusals,
  type RegistrationForm,
} from '../register/register';
import { lengthWrong, mismatch, newMismatch, OWN_PASSWORD_REFUSALS } from './account';

const refusal = (status: number, code?: string) =>
  new ApiError(status, { status, error: 'Refused', message: 'in English', code });

function filled(changes: Partial<RegistrationForm> = {}): RegistrationForm {
  return {
    ...emptyRegistration('DNI'), fullName: 'Ana Pérez', documentNumber: '30111222', birthDate: '1990-05-14',
    email: 'ana@mail.com', password: 'una-clave-larga', repeated: 'una-clave-larga', ...changes,
  };
}

describe('a password', () => {
  it('is sent only when typed the same twice', () => {
    expect(mismatch('una-clave-larga', 'una-clave-larga')).toBeNull();
    expect(mismatch('una-clave-larga', 'otra-clave-larga')).toBe('Las dos contraseñas no coinciden.');
    expect(newMismatch('una-clave-larga', 'otra')).toBe('Las dos contraseñas nuevas no coinciden.');
    expect(newMismatch('una-clave-larga', 'una-clave-larga')).toBeNull();
  });

  it('is from 10 characters to 72 bytes, as the server counts it', () => {
    expect(lengthWrong('123456789')).toBe(true);
    expect(lengthWrong('1234567890')).toBe(false);
    expect(lengthWrong('a'.repeat(72))).toBe(false);
    expect(lengthWrong('ñ'.repeat(37))).toBe(true);
  });

  it("says each refusal of changing one's own in the Thymeleaf screen's words", () => {
    expect(describeError(refusal(400, 'WRONG_CURRENT'), OWN_PASSWORD_REFUSALS)).toBe('La contraseña actual no es esa.');
    expect(describeError(refusal(400, 'SAME'), OWN_PASSWORD_REFUSALS))
      .toBe('La contraseña nueva es igual a la actual: elegí otra.');
    expect(describeError(refusal(400, 'ILLEGAL_ARGUMENT'), OWN_PASSWORD_REFUSALS))
      .toBe('La contraseña tiene de 10 a 72 caracteres.');
  });
});

describe('registering on the web', () => {
  it('offers three children, and sends only those typed, an empty value as none', () => {
    const form = filled();
    expect(form.children).toHaveLength(CHILD_SLOTS);
    form.children[1] = { fullName: 'Tomás Pérez', documentType: 'DNI', documentNumber: '', birthDate: '',
      relation: 'FATHER' };
    expect(registrationBody(form)).toEqual({
      fullName: 'Ana Pérez', documentType: 'DNI', documentNumber: '30111222', birthDate: '1990-05-14', phone: null,
      email: 'ana@mail.com', password: 'una-clave-larga',
      children: [{ fullName: 'Tomás Pérez', documentType: 'DNI', documentNumber: '', birthDate: null,
        relation: 'FATHER' }],
    });
    expect(registrationBody(filled({ phone: '1155667788' })).phone).toBe('1155667788');
  });

  it('counts a block with only its number or its date of birth as a child', () => {
    const byNumber = filled();
    byNumber.children[0] = { ...byNumber.children[0], documentNumber: '50111222' };
    const byDate = filled();
    byDate.children[2] = { ...byDate.children[2], birthDate: '2020-01-02' };
    expect(registrationBody(byNumber).children).toHaveLength(1);
    expect(registrationBody(byDate).children).toHaveLength(1);
  });

  it('says what the form missed when the API says only that it is malformed', () => {
    expect(malformed(filled({ birthDate: '' }))).toBe('Falta tu fecha de nacimiento.');
    const child = filled();
    child.children[0] = { ...child.children[0], fullName: 'Tomás' };
    expect(malformed(child)).toBe('Falta la fecha de nacimiento de uno de tus hijos.');
    expect(malformed(filled({ password: 'corta' }))).toBe('La contraseña tiene de 10 a 72 caracteres.');
    expect(malformed(filled())).toBe('Pusiste el mismo documento dos veces.');
  });

  it("says each refusal in the Thymeleaf screen's words", () => {
    const said = (error: ApiError, form = filled()) => describeError(error, registrationRefusals(form));
    expect(said(refusal(409, 'UNDER_EIGHTEEN'))).toContain('mayores de 18 años');
    expect(said(refusal(409, 'NOT_A_CHILD'))).toContain('hijos menores de 13');
    expect(said(refusal(400, 'INVALID_ACCOUNT_EMAIL'))).toContain('nombre@dominio.com');
    expect(said(refusal(400, 'INVALID_REGISTRATION'))).toContain('los nombres, los documentos');
    expect(said(refusal(400, 'ILLEGAL_ARGUMENT'), filled({ birthDate: '' }))).toBe('Falta tu fecha de nacimiento.');
    expect(said(refusal(503))).toBe('No pudimos mandarte el email. Probá de nuevo en un rato.');
  });

  it('names the children a confirmation could not register, and nothing when it registered all', () => {
    expect(notRegisteredSaid([])).toBeNull();
    expect(notRegisteredSaid(['Tomás Pérez', 'Sofía Pérez']))
      .toBe('No pudimos registrar a Tomás Pérez, Sofía Pérez con ese documento: acercate a recepción con su documento.');
  });
});

describe('when an appointment is', () => {
  it("is said as the approved mock says it, read as the practice's time", () => {
    expect(dayAndTime('2026-03-12T10:00:00')).toBe('Jueves 12 de marzo, 10:00');
    expect(dayAndTime('2026-10-05T09:05:00')).toBe('Lunes 5 de octubre, 09:05');
  });
});
