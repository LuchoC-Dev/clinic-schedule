import { byCode } from '../shared/refusals';

/**
 * Reception's words (D-13): the names the screens give the API's values, and each refusal said by its code
 * (D-100), as the Thymeleaf screens say them.
 */

export const DOCUMENT_TYPES = ['DNI', 'LC', 'LE', 'CI', 'PASSPORT'] as const;

export const DOCUMENT_TYPE_NAMES: Record<string, string> = {
  DNI: 'DNI', LC: 'LC', LE: 'LE', CI: 'CI', PASSPORT: 'Pasaporte',
};

/** Who a phone's or an email's owner is to the patient (D-73), in the forms' order. */
export const RELATIONS: Record<string, string> = {
  MOTHER: 'Madre',
  FATHER: 'Padre',
  GUARDIAN: 'Tutor/a',
  PARTNER: 'Pareja',
  CHILD: 'Hijo/a',
  OTHER: 'Otra persona',
};

export const IDENTITY: Record<string, string> = {
  ALIAS: 'Sin datos',
  DECLARED: 'No verificada',
  VERIFIED: 'Verificada',
};

export const DETAIL_FIELDS: Record<string, string> = {
  BIRTH_DATE: 'Fecha de nacimiento',
  PHONE: 'Teléfono',
  EMAIL: 'Email',
  INSURANCE: 'Obra social',
  PHONE_CONSENT: 'Recordatorios al teléfono',
  EMAIL_CONSENT: 'Recordatorios al email',
};

export const CONSENT: Record<string, string> = {
  PATIENT: 'Lo dio el paciente',
  OWNER: 'Lo dio la persona responsable',
};

export const MEANS: Record<string, string> = {
  CASH: 'Efectivo',
  DEBIT: 'Débito',
  CREDIT: 'Crédito',
  TRANSFER: 'Transferencia',
  QR: 'QR',
};

export const REQUESTER: Record<string, string> = {
  PATIENT: 'el paciente',
  PRACTICE: 'el consultorio',
};

/** Who did something, as reception reads it: a patient's web account is the patient (D-81). */
export function who(username: string | null | undefined): string {
  if (!username) {
    return '—';
  }
  return username.startsWith('portal:') ? 'el paciente (web)' : username;
}

/** Booking, moving, cancelling and warning (D-23). */
export const AGENDA_REFUSALS = byCode({
  PRACTITIONER_ALREADY_BOOKED: 'El profesional ya tiene un turno en ese horario.',
  PATIENT_ALREADY_BOOKED: 'El paciente ya tiene otro turno en ese horario.',
  SLOT_IN_THE_PAST: 'Ese horario ya pasó.',
  SLOT_TOO_FAR_AHEAD: 'No se reservan turnos con más de un año de anticipación: revisá el año.',
  NOT_THE_APPOINTMENT_DAY: 'Eso solo se puede hacer el día del turno.',
  OUTSIDE_WORKING_HOURS: 'El profesional no atiende en ese horario.',
  BOOKING_RULE_VIOLATION:
    'No se puede: el turno ya fue cancelado, o el paciente ya había llegado o avisado su demora.',
  APPOINTMENT_NOT_FOUND: 'El turno no existe.',
  UNKNOWN_PATIENT: 'El paciente no está registrado.',
  UNKNOWN_PRACTITIONER: 'El profesional no existe.',
  UNKNOWN_REASON: 'Esa razón no corresponde al profesional.',
});

/** A new patient registered as they are booked (D-75). */
export const NEW_PATIENT_REFUSALS = byCode({
  DUPLICATE_DOCUMENT: 'Ese documento ya pertenece a otro paciente: buscalo y elegilo.',
  INCOMPLETE_DOCUMENT: 'El documento del paciente nuevo necesita tipo y número, o ninguno de los dos.',
  INVALID_DATA: 'Revisá el paciente nuevo: el nombre lleva letras y hasta 150 caracteres; el DNI, 7 u 8 dígitos; '
    + 'LE y LC, de 6 a 8 dígitos; CI y pasaporte, solo letras y números.',
  INVALID_DETAILS: 'Revisá el contacto del paciente nuevo: el teléfono lleva de 8 a 15 dígitos; el email, la forma '
    + 'nombre@dominio.com; y si son de otra persona, van con su nombre y su vínculo.',
});

/** A patient's identity and details (D-26, D-60, D-73, D-74, D-77, D-79). */
export const PATIENT_REFUSALS = byCode({
  DUPLICATE_DOCUMENT: 'Ese documento ya pertenece a otro paciente.',
  INCOMPLETE_DOCUMENT: 'El documento necesita tipo y número, o ninguno de los dos.',
  UNVERIFIABLE_IDENTITY: 'Para marcar que viste el documento, el paciente necesita su documento y su nombre real.',
  VERIFICATION_REQUIRED: 'El paciente tiene cuenta web: su nombre o su documento solo cambian si viste el documento '
    + 'y lo marcás.',
  INVALID_PATIENT_DETAILS: 'Revisá los datos personales: la fecha de nacimiento no puede ser futura ni de hace más '
    + 'de 130 años; el teléfono lleva de 8 a 15 dígitos; el email, la forma nombre@dominio.com; si el teléfono o el '
    + 'email son de otra persona, van con su nombre y su vínculo; y el número de afiliado y el plan van con el nombre '
    + 'de la obra social, de hasta 100 caracteres (el afiliado, hasta 30; el plan, hasta 50).',
  ILLEGAL_ARGUMENT: 'Revisá los datos: el nombre lleva letras y hasta 150 caracteres; el DNI, 7 u 8 dígitos; LE y LC, '
    + 'de 6 a 8 dígitos; CI y pasaporte, solo letras y números.',
  PATIENT_NOT_FOUND: 'No se encontró el paciente.',
});

/** Bringing a patient in and charging their visit (D-64, D-65, D-87, D-89, D-90). */
export const ARRIVAL_REFUSALS = byCode({
  QUEUE_ALREADY_OPEN: 'La cola ya está abierta.',
  ALREADY_WAITING: 'Ese paciente ya está esperando en esta cola.',
  ALREADY_ARRIVED: 'El paciente de ese turno ya llegó.',
  ALREADY_KEPT_FOR_THEM: 'Ese paciente ya espera a ese profesional.',
  NOT_KEPT: 'Ese paciente no espera a nadie en particular.',
  ALREADY_EMERGENCY: 'Ese paciente ya es una urgencia.',
  NOT_WAITING: 'Ese paciente ya no está esperando.',
  AUTO_CLOSE_ALREADY_OFF: 'La cola ya no se cierra sola.',
  NOT_BOOKED: 'Ese turno no está reservado: fue cancelado o el paciente estuvo ausente.',
  NOT_TODAY: 'Con un turno se llega el día del turno.',
  NO_QUEUE: 'El profesional de ese turno no atiende ninguna cola.',
  CHARGE_REQUIRED: 'Falta el importe del cobro: sin cobro ni cobertura, solo entra una urgencia.',
  INVALID_AMOUNT: 'El importe debe ser mayor que cero, con dos decimales como mucho.',
  AMOUNT_TOO_HIGH: 'El importe supera el máximo que acepta la caja.',
  NOT_INSURED: 'El paciente no tiene obra social cargada: paga como particular.',
  IDENTITY_NOT_VERIFIED: 'La identidad del paciente no está verificada: paga como particular.',
  TOKEN_TOO_LONG: 'El token no puede superar los 50 caracteres.',
  QUEUE_NOT_OPEN: 'La cola está cerrada: abrila primero.',
  ENTRY_NOT_FOUND: 'Ese paciente no está en la cola.',
  UNKNOWN_PATIENT: 'Ese paciente no existe.',
  STREAM_CHANGED: 'La cola cambió mientras tanto. Revisala y probá de nuevo.',
});

/** Where a patient is in a queue (D-63). */
export const ENTRY_STATES: Record<string, string> = {
  WAITING: 'Esperando',
  CALLED: 'Llamado',
  SEEN: 'Atendido',
  LEFT: 'Se fue',
  NOT_SEEN: 'No atendido',
  DID_NOT_SHOW_UP: 'No se presentó',
};

/** A member's age, which decides how they join and whether they decide for themselves (D-84). */
export const AGE_BANDS: Record<string, string> = {
  CHILD: 'menor de 13',
  ADOLESCENT: 'de 13 a 17',
  ADULT: 'adulto',
};

/** What a child is to the adult who asked for them (D-84). */
export const CHILD_RELATIONS: Record<string, string> = {
  MOTHER: 'madre',
  FATHER: 'padre',
  GUARDIAN: 'tutor o tutora',
};

export const FAMILY_CHANGES: Record<string, string> = {
  CREATED: 'Grupo creado',
  JOINED: 'Entró',
  CHILD_ADDED: 'Agregado como menor',
  REMOVED: 'Salió',
  SHARING_STARTED: 'Empezó a compartir sus turnos',
  SHARING_STOPPED: 'Dejó de compartir sus turnos',
  RELATION_PROVED: 'Vínculo probado',
};

/** A family group's rules (D-84, D-92). */
export const FAMILY_REFUSALS = byCode({
  NO_BIRTH_DATE: 'Para entrar a un grupo, el paciente necesita su fecha de nacimiento cargada.',
  A_CHILD: 'Es menor de 13: se agrega como menor, a pedido de su madre, padre o tutor del grupo.',
  NOT_A_CHILD: 'Tiene 13 años o más: entra por sí mismo, presente y aceptando.',
  ALREADY_A_MEMBER: 'Ya está en el grupo.',
  NOT_A_MEMBER: 'No está en el grupo.',
  NOT_AN_ADULT_MEMBER: 'Un menor se agrega a pedido de un adulto del grupo.',
  NO_PROOF: 'Para agregar un menor, mirá la documentación que acredita el vínculo y marcalo.',
  CHILDREN_SHARE: 'Un menor de 13 comparte siempre sus turnos con su familia.',
  NOTHING_TO_PROVE: 'Ese miembro no tiene un vínculo por probar.',
  FAMILY_GROUP_NOT_FOUND: 'El grupo no existe.',
  UNKNOWN_PATIENT: 'Ese paciente no existe.',
});

export const ACCOUNT_STATUS: Record<string, string> = {
  PENDING: 'Pendiente de activación',
  ACTIVE: 'Activa',
  INACTIVE: 'Desactivada',
};

export const ACCOUNT_CHANGES: Record<string, string> = {
  OPENED: 'Abierta',
  ACTIVATION_SENT: 'Link de activación enviado',
  ACTIVATED: 'Activada',
  EMAIL_CHANGED: 'Email cambiado',
  DEACTIVATED: 'Desactivada',
  REACTIVATED: 'Reactivada',
  RECOVERY_SENT: 'Link para elegir contraseña enviado',
  PASSWORD_RESET: 'Contraseña nueva desde el link',
  PASSWORD_CHANGED: 'Contraseña cambiada',
  LOCKED: 'Bloqueada por intentos fallidos',
  REGISTERED: 'Abierta al confirmar el registro desde la web',
  CHILD_REQUESTED: 'Pidió registrar a un hijo desde la web',
};

/** Who did something to an account: reception's user, or the patient, nobody being at reception (D-86). */
export function accountChangeBy(action: string, by: string | null): string {
  if (by) {
    return by;
  }
  if (action === 'RECOVERY_SENT') {
    return 'pedido desde la web';
  }
  return action === 'LOCKED' ? '' : 'el paciente';
}

/** A patient's web account (D-79, D-86). */
export const ACCOUNT_REFUSALS = byCode({
  ALREADY_OPENED: 'El paciente ya tiene cuenta web.',
  IDENTITY_NOT_VERIFIED: 'Para abrirle una cuenta web, primero verificá su identidad: mirá su documento y marcalo.',
  NO_BIRTH_DATE: 'Para abrirle una cuenta web, cargá primero su fecha de nacimiento.',
  UNDER_THIRTEEN: 'La cuenta web es para pacientes de 13 años o más.',
  INVALID_ACCOUNT_EMAIL: 'El email tiene la forma nombre@dominio.com y hasta 254 caracteres.',
  ACCOUNT_STATE: 'La cuenta ya no está en ese estado: mirá cómo quedó.',
});
