/** A patient's web account as reception reads it (D-79): never its password. */
export interface Account {
  id: string;
  patientId: string;
  email: string;
  status: 'PENDING' | 'ACTIVE' | 'INACTIVE';
  openedAt: string;
  /** Whether the activation's email went out, for what sends one. */
  activationSent?: boolean;
}

/**
 * What reception may do with an account, as on the Thymeleaf screen (D-79): send the activation again while it is
 * pending; switch it off unless it is off; switch it on when it is.
 */
export function accountActions(status: Account['status']) {
  return {
    resend: status === 'PENDING',
    deactivate: status !== 'INACTIVE',
    reactivate: status === 'INACTIVE',
  };
}

/**
 * What the screen says once something is done to the account, in the Thymeleaf screen's words: whether the
 * activation's email went out when one was sent, and how a reactivated account stands.
 */
export function sentSaid(path: string, account: Account): string {
  if (path === '') {
    return account.activationSent ? `Cuenta abierta: le enviamos el link de activación a ${account.email}.`
      : 'Cuenta abierta, pero el email de activación no salió: reenvialo.';
  }
  if (path.endsWith('/activation-resend')) {
    return account.activationSent ? `Le reenviamos el link de activación a ${account.email}. El anterior ya no sirve.`
      : 'El email de activación no salió: probá de nuevo más tarde.';
  }
  if (path.endsWith('/email-change')) {
    if (account.status !== 'PENDING') return `Email de la cuenta: ${account.email}.`;
    return account.activationSent ? `Email cambiado: le enviamos el link de activación a ${account.email}.`
      : 'Email cambiado, pero el link de activación no salió: reenvialo.';
  }
  if (path.endsWith('/deactivate')) {
    return 'Cuenta desactivada: ya no puede ingresar.';
  }
  return account.status === 'ACTIVE' ? 'Cuenta reactivada: puede ingresar con su contraseña.'
    : 'Cuenta reactivada: nunca se activó, así que reenviale el link de activación.';
}
