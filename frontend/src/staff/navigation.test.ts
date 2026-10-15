import { describe, expect, it } from 'vitest';

import { type Role, type StaffUser, landingFor, menuFor } from './navigation';

function user(role: Role, temporaryPassword = false): StaffUser {
  return { username: 'alguien', role, practitionerId: null, temporaryPassword };
}

const labels = (role: Role) => menuFor(user(role)).map((entry) => entry.label);

describe('the menu of each role', () => {
  it("offers reception the agenda of this application first, and the rest of reception's screens", () => {
    const menu = menuFor(user('RECEPTION'));
    expect(menu[0]).toEqual({ label: 'Agenda', to: '/agenda', inApp: true });
    expect(labels('RECEPTION')).toEqual(['Agenda', 'Llegadas', 'Pacientes', 'Grupos familiares', 'Profesionales', 'Colas',
      'Cancelaciones', 'Recordatorios', 'Caja', 'Copias para retirar', 'Sala de espera']);
    expect(menu.find((entry) => entry.label === 'Sala de espera')?.newTab).toBe(true);
  });

  it("offers a practitioner their queue first and their agenda in this application, and nothing of reception's", () => {
    expect(labels('PRACTITIONER')).toEqual(['Mi cola', 'Mi agenda', 'Estudios pendientes', 'Mis órdenes',
      'Copias pedidas']);
    expect(menuFor(user('PRACTITIONER')).slice(0, 4)).toEqual([
      { label: 'Mi cola', to: '/my-queue', inApp: true }, { label: 'Mi agenda', to: '/my-agenda', inApp: true },
      { label: 'Estudios pendientes', to: '/checks/pending', inApp: true },
      { label: 'Mis órdenes', to: '/orders/mine', inApp: true }]);
  });

  it('offers an admin the users and the logs, and no clinical screen', () => {
    expect(labels('ADMIN')).toEqual(['Usuarios', 'Lecturas', 'Rechazos', 'Cancelaciones', 'Caja']);
  });

  it('offers the waiting room nothing', () => {
    expect(labels('WAITING_ROOM')).toEqual([]);
  });
});

describe('where each one lands', () => {
  it('lands each role on the first entry of their menu, and the waiting room on its screen', () => {
    expect(landingFor(user('RECEPTION'))).toEqual({ label: 'Agenda', to: '/agenda', inApp: true });
    expect(landingFor(user('ADMIN')).to).toBe('/users');
    expect(landingFor(user('PRACTITIONER'))).toEqual({ label: 'Mi cola', to: '/my-queue', inApp: true });
    expect(landingFor(user('WAITING_ROOM')).to).toBe('/waiting-room');
  });

  it('lands anyone whose password is temporary on changing it first', () => {
    for (const role of ['RECEPTION', 'PRACTITIONER', 'ADMIN', 'WAITING_ROOM'] as Role[]) {
      expect(landingFor(user(role, true))).toEqual({ label: 'Contraseña', to: '/password?required', inApp: false });
    }
  });
});
