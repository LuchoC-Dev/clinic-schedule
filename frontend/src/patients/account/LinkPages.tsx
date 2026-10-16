import { Alert, Anchor, Loader, Stack, Text, Title } from '@mantine/core';
import type { ReactNode } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';

import { ApiError } from '../../shared/api';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { NEW_PASSWORD_REFUSALS, UNUSABLE_ACTIVATION, UNUSABLE_RESET } from './account';
import { NewPasswordForm } from './NewPasswordForm';
import { useLinkCheck } from './useLinkCheck';

/**
 * The screens an email's link opens to choose a password (D-110): activating the account (D-79), and a new one after
 * asking for it (D-86). Both say first whether the link still works.
 */

/** Activating the account reception opened, with the password the patient chooses. */
export function ActivationPage() {
  return (
    <ChoosePassword title="Activá tu cuenta" intro="Elegí tu contraseña: de 10 a 72 caracteres, los que quieras."
      check="/api/portal/activation" send="/api/portal/activation" label="Contraseña" action="Activar"
      unusable={UNUSABLE_ACTIVATION} done="activated" />
  );
}

/** Choosing a new password from the recovery's link. */
export function PasswordResetPage() {
  return (
    <ChoosePassword title="Elegí una contraseña nueva" intro="De 10 a 72 caracteres, los que quieras."
      check="/api/portal/password-reset" send="/api/portal/password-reset" label="Contraseña nueva" action="Guardar"
      unusable={<>{UNUSABLE_RESET.replace(/recuperar tu contraseña\.$/, '')}
        <Anchor component={Link} to="/password-recovery">recuperar tu contraseña</Anchor>.</>}
      done="reset" />
  );
}

function ChoosePassword({ title, intro, check, send, label, action, unusable, done }: {
  title: string;
  intro: string;
  check: string;
  send: string;
  label: string;
  action: string;
  unusable: ReactNode;
  /** What the login says once it is chosen. */
  done: 'activated' | 'reset';
}) {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const link = useLinkCheck(check, token);
  const choose = useAction((password: string) => api.post(send, { token, password }), {
    refusals: NEW_PASSWORD_REFUSALS,
    onDone: () => navigate(`/login?${done}`),
  });
  // Used meanwhile, or ended by a newer one: the link is no use any more.
  const spent = choose.failure instanceof ApiError && choose.failure.status === 400
    && choose.failure.body?.code !== 'ILLEGAL_ARGUMENT';

  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>{title}</Title>
      {link.unusable || spent ? <Alert color="red" role="alert">{unusable}</Alert>
        : link.error ? <Alert color="red" role="alert">{link.error}</Alert>
          : !link.usable ? <Loader size="sm" aria-label="Revisando el link" />
            : (
              <>
                <Text c="dimmed">{intro}</Text>
                <NewPasswordForm label={label} action={action} pending={choose.pending} error={choose.error}
                  onSend={choose.run} />
              </>
            )}
      <Anchor component={Link} to="/login">Ingresar</Anchor>
    </Stack>
  );
}
