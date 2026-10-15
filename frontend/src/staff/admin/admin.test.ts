import { describe, expect, it } from 'vitest';

import { ApiError } from '../../shared/api';
import { describeError } from '../../shared/errors';
import {
  cancelledByForAdmins, LOG_REFUSALS, loggedPatient, loggedPractitioner, newUser, readingWhat, refusalWhat, USER_REFUSALS,
  userState,
} from './admin';

const refused = (status: number, code: string) => new ApiError(status, { status, error: 'x', message: 'english', code });

describe('a new user', () => {
  it('stands for a practitioner only when their role is a practitioner', () => {
    expect(newUser({ username: ' arias.b ', role: 'PRACTITIONER', practitionerId: 'p1', password: 'una-clave-larga' }))
      .toEqual({ username: 'arias.b', role: 'PRACTITIONER', practitionerId: 'p1', password: 'una-clave-larga' });
    expect(newUser({ username: 'mesa', role: 'RECEPTION', practitionerId: 'p1', password: 'una-clave-larga' }).practitionerId)
      .toBeNull();
  });

  it('is sent without a role or a practitioner when none was chosen, for the server to refuse it', () => {
    expect(newUser({ username: 'x', role: '', practitionerId: '', password: ' clave ' }))
      .toEqual({ username: 'x', role: null, practitionerId: null, password: ' clave ' });
    expect(newUser({ username: 'x', role: 'PRACTITIONER', practitionerId: '', password: 'p' }).practitionerId).toBeNull();
  });

  it('says whether a user is active', () => {
    expect(userState({ active: true })).toBe('Activo');
    expect(userState({ active: false })).toBe('Desactivado');
  });

  it("says each refusal in the users' screens' words", () => {
    expect(describeError(refused(409, 'USERNAME_TAKEN'), USER_REFUSALS)).toMatch(/^Ese nombre de usuario ya se usó/);
    expect(describeError(refused(409, 'PRACTITIONER_TAKEN'), USER_REFUSALS)).toBe('Ese profesional ya tiene un usuario activo.');
    expect(describeError(refused(409, 'SELF_DEACTIVATION'), USER_REFUSALS)).toBe('No podés desactivar tu propio usuario.');
    expect(describeError(refused(409, 'USER_STATUS'), USER_REFUSALS)).toBe('El usuario ya estaba así.');
    expect(describeError(refused(400, 'ILLEGAL_ARGUMENT'), USER_REFUSALS)).toContain('de 10 a 72 caracteres');
  });
});

describe('the logs', () => {
  it('name a patient with their document, without it, or as unknown', () => {
    expect(loggedPatient({ id: 'p', fullName: 'Ana Pérez', document: 'DNI 30111222' })).toBe('Ana Pérez — DNI 30111222');
    expect(loggedPatient({ id: 'p', fullName: 'Ana Pérez', document: null })).toBe('Ana Pérez');
    expect(loggedPatient({ id: 'p', fullName: null, document: null })).toBe('(paciente desconocido)');
    expect(loggedPatient(null)).toBe('—');
  });

  it('name a practitioner, one unknown, or nobody', () => {
    expect(loggedPractitioner({ id: 'x', fullName: 'Dra. Arias' })).toBe('Dra. Arias');
    expect(loggedPractitioner({ id: 'x', fullName: null })).toBe('(profesional desconocido)');
    expect(loggedPractitioner(null)).toBe('—');
  });

  it('say what was read, never which file', () => {
    expect(readingWhat({ attachment: true, copy: false })).toBe('Archivo adjunto');
    expect(readingWhat({ attachment: false, copy: true })).toBe('Copia de la historia');
    expect(readingWhat({ attachment: false, copy: false })).toBe('Historia');
  });

  it('say what was tried, a route with its method and path', () => {
    expect(refusalWhat({ attempt: 'RECORD', route: null })).toBe('Historia');
    expect(refusalWhat({ attempt: 'ATTACHMENT', route: null })).toBe('Archivo adjunto');
    expect(refusalWhat({ attempt: 'ROUTE', route: 'GET /api/clinical-records/x' })).toBe('Ruta GET /api/clinical-records/x');
  });

  it("say a patient's cancellation on the web as the patient's, never their account", () => {
    expect(cancelledByForAdmins({ origin: 'ONLINE', cancelledBy: null })).toBe('paciente (web)');
    expect(cancelledByForAdmins({ origin: 'EMAIL_LINK', cancelledBy: null })).toBe('paciente (web)');
    expect(cancelledByForAdmins({ origin: 'BY_HAND', cancelledBy: 'recepcion' })).toBe('recepcion');
    expect(cancelledByForAdmins({ origin: 'TIME_OFF', cancelledBy: 'recepcion' })).toBe('recepcion');
  });

  it('say two days the wrong way round', () => {
    expect(describeError(refused(400, 'ILLEGAL_ARGUMENT'), LOG_REFUSALS)).toBe('El primer día es posterior al último.');
  });
});
