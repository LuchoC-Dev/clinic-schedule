/**
 * Where each role of the staff goes, and what its menu offers (D-97). The same as the Thymeleaf screens' header
 * offers each role (D-34, D-68, D-70): this application only moves the screens, never what a role may reach,
 * which the server decides by route anyway.
 *
 * While the screens move over, a screen not built here yet is the Thymeleaf one, reached with a plain link: both
 * share the session, so the person stays logged in. Each part of the migration turns its entries into the
 * application's own.
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
  /** A route of this application when `inApp`, otherwise the address of a Thymeleaf screen. */
  to: string;
  inApp: boolean;
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

const inApp = (label: string, to: string): MenuEntry => ({ label, to, inApp: true });
const screen = (label: string, to: string): MenuEntry => ({ label, to, inApp: false });

const MENUS: Record<Role, MenuEntry[]> = {
  RECEPTION: [
    inApp('Agenda', '/agenda'),
    inApp('Llegadas', '/arrivals'),
    inApp('Pacientes', '/patients'),
    inApp('Grupos familiares', '/family-groups'),
    screen('Profesionales', '/practitioners'),
    screen('Colas', '/queues'),
    inApp('Cancelaciones', '/cancellations'),
    inApp('Recordatorios', '/reminders'),
    inApp('Caja', '/cash-desk'),
    screen('Copias para retirar', '/record-copy-envelopes'),
    { ...screen('Sala de espera', '/waiting-room'), newTab: true },
  ],
  // As the approved mock has it: their queue first, where the day's work is, and where they land (D-104).
  PRACTITIONER: [
    inApp('Mi cola', '/my-queue'),
    inApp('Mi agenda', '/my-agenda'),
    inApp('Estudios pendientes', '/checks/pending'),
    inApp('Mis órdenes', '/orders/mine'),
    { ...inApp('Copias pedidas', '/record-copies'), counts: 'record-copies' },
  ],
  ADMIN: [
    inApp('Usuarios', '/users'),
    inApp('Lecturas', '/record-readings'),
    inApp('Rechazos', '/record-refusals'),
    inApp('Cancelaciones', '/cancellation-log'),
    inApp('Caja', '/cash-desk-log'),
  ],
  WAITING_ROOM: [],
};

/** Every role changes its own password, on the Thymeleaf screen until it moves here. */
export const PASSWORD = screen('Contraseña', '/password');

export function menuFor(user: StaffUser): MenuEntry[] {
  return MENUS[user.role];
}

/**
 * Where a user lands on logging in, as on the screens: changing a temporary password first, then the first entry
 * of their menu — the waiting room, which has none, on its screen.
 */
export function landingFor(user: StaffUser): MenuEntry {
  if (user.temporaryPassword) {
    return screen('Contraseña', '/password?required');
  }
  return MENUS[user.role][0] ?? screen('Sala de espera', '/waiting-room');
}
