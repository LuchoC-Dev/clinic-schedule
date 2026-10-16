/**
 * Where each role of the staff goes, and what its menu offers (D-97): what each role may reach (D-34, D-68, D-70),
 * which the server decides by route anyway.
 */

export type Role = 'RECEPTION' | 'PRACTITIONER' | 'ADMIN' | 'WAITING_ROOM';

export interface StaffUser {
  username: string;
  role: Role;
  practitionerId: string | null;
  /** An admin set the password: it must be changed before anything else (D-69). */
  temporaryPassword: boolean;
}

export interface MenuEntry {
  label: string;
  /** A route of this application. */
  to: string;
  newTab?: boolean;
  /** What the entry counts beside it, while there is any: the copies waiting for a practitioner (D-106). */
  counts?: 'record-copies';
}

export const ROLE_NAMES: Record<Role, string> = {
  RECEPTION: 'Recepción',
  PRACTITIONER: 'Profesional',
  ADMIN: 'Administración',
  WAITING_ROOM: 'Sala de espera',
};

const entry = (label: string, to: string): MenuEntry => ({ label, to });

const MENUS: Record<Role, MenuEntry[]> = {
  RECEPTION: [
    entry('Agenda', '/agenda'),
    entry('Llegadas', '/arrivals'),
    entry('Pacientes', '/patients'),
    entry('Grupos familiares', '/family-groups'),
    entry('Profesionales', '/practitioners'),
    entry('Colas', '/queues'),
    entry('Cancelaciones', '/cancellations'),
    entry('Recordatorios', '/reminders'),
    entry('Caja', '/cash-desk'),
    entry('Copias para retirar', '/record-copy-envelopes'),
    { ...entry('Sala de espera', '/waiting-room'), newTab: true },
  ],
  // As the approved mock has it: their queue first, where the day's work is, and where they land (D-104).
  PRACTITIONER: [
    entry('Mi cola', '/my-queue'),
    entry('Mi agenda', '/my-agenda'),
    entry('Estudios pendientes', '/checks/pending'),
    entry('Mis órdenes', '/orders/mine'),
    { ...entry('Copias pedidas', '/record-copies'), counts: 'record-copies' },
  ],
  ADMIN: [
    entry('Usuarios', '/users'),
    entry('Lecturas', '/record-readings'),
    entry('Rechazos', '/record-refusals'),
    entry('Cancelaciones', '/cancellation-log'),
    entry('Caja', '/cash-desk-log'),
  ],
  WAITING_ROOM: [],
};

/** Every role changes its own password (D-69, D-110). */
export const PASSWORD = entry('Contraseña', '/password');

export function menuFor(user: StaffUser): MenuEntry[] {
  return MENUS[user.role];
}

/**
 * Where a user lands on logging in: changing a temporary password first, then the first entry
 * of their menu — the waiting room, which has none, on its screen.
 */
export function landingFor(user: StaffUser): MenuEntry {
  if (user.temporaryPassword) {
    return PASSWORD;
  }
  return MENUS[user.role][0] ?? entry('Sala de espera', '/waiting-room');
}
