import { byCode } from '../../shared/refusals';

/** A password typed twice: the new one and its repetition must be the same before anything is sent (D-69). */
export function mismatch(newPassword: string, repeated: string): string | null {
  return newPassword === repeated ? null : 'La contraseña nueva y su repetición no coinciden.';
}

/** Changing one's own password, refused in the Thymeleaf screen's words. */
export const PASSWORD_REFUSALS = byCode({
  WRONG_PASSWORD: 'La contraseña actual no es correcta.',
  UNCHANGED_PASSWORD: 'La contraseña nueva tiene que ser distinta de la actual.',
  ILLEGAL_ARGUMENT: 'La contraseña nueva lleva de 10 a 72 caracteres.',
});
