import { byCode, combine } from '../../shared/refusals';

/**
 * What the account's screens decide (D-79, D-86): a password typed twice, its length, and each refusal in the
 * Thymeleaf screens' words (D-100).
 */

/** The password and its repetition must be the same before anything is sent. */
export function mismatch(password: string, repeated: string): string | null {
  return password === repeated ? null : 'Las dos contraseñas no coinciden.';
}

/** The patient's new passwords, as the Thymeleaf screen of changing it says it. */
export function newMismatch(password: string, repeated: string): string | null {
  return password === repeated ? null : 'Las dos contraseñas nuevas no coinciden.';
}

/** A password's length, as the server counts it: from 10 characters to 72 bytes (D-68). */
export function lengthWrong(password: string): boolean {
  return password.length < 10 || new TextEncoder().encode(password).length > 72;
}

export const PASSWORD_LENGTH = 'La contraseña tiene de 10 a 72 caracteres.';

export const UNUSABLE_ACTIVATION = 'Este link ya no sirve: venció o ya se usó. Si todavía no activaste tu cuenta, pedí '
  + 'un link nuevo en recepción.';

export const UNUSABLE_RESET = 'Este link ya no sirve: venció, ya se usó o pediste otro después. Pedí uno nuevo desde '
  + 'recuperar tu contraseña.';

export const RECOVERY_SENT = 'Si ese documento tiene una cuenta, le mandamos un link al email de la cuenta para '
  + 'elegir una contraseña nueva. Vale una hora.';

/** A new password chosen from a link: the activation's or the recovery's. */
export const NEW_PASSWORD_REFUSALS = byCode({ ILLEGAL_ARGUMENT: PASSWORD_LENGTH });

/** The patient changes their own, logged in. */
export const OWN_PASSWORD_REFUSALS = combine(byCode({
  WRONG_CURRENT: 'La contraseña actual no es esa.',
  SAME: 'La contraseña nueva es igual a la actual: elegí otra.',
}), NEW_PASSWORD_REFUSALS);
