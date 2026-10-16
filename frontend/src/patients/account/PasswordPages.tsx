import { Anchor, Button, NativeSelect, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { type FormEvent, useContext, useState } from 'react';
import { Link } from 'react-router';

import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api, SessionContext } from '../session';
import { newMismatch, OWN_PASSWORD_REFUSALS, RECOVERY_SENT } from './account';

/**
 * Asking for the link to choose a new password, with the document (D-86): the same answer whether the document has
 * an account or not.
 */
export function PasswordRecoveryPage() {
  const { documentTypes } = useContext(SessionContext);
  const [documentType, setDocumentType] = useState(documentTypes[0] ?? 'DNI');
  const [documentNumber, setDocumentNumber] = useState('');
  const ask = useAction(() => api.post('/api/portal/password-recovery', { documentType, documentNumber }), {
    done: RECOVERY_SENT,
    onDone: () => setDocumentNumber(''),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    ask.run(undefined);
  }

  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>Recuperar tu contraseña</Title>
      <Said error={ask.error} notice={ask.notice} />
      <Text c="dimmed">Te mandamos un link al email de tu cuenta para elegir una contraseña nueva.</Text>
      <form onSubmit={submit}>
        <Stack gap={16}>
          <NativeSelect label="Tipo de documento" data={documentTypes} value={documentType} size="md"
            onChange={(event) => setDocumentType(event.currentTarget.value)} />
          <TextInput label="Número" autoComplete="username" required maxLength={20} size="md" value={documentNumber}
            onChange={(event) => setDocumentNumber(event.currentTarget.value)} />
          <Button type="submit" size="md" loading={ask.pending}>Mandame el link</Button>
        </Stack>
      </form>
      <Anchor component={Link} to="/login">Ingresar</Anchor>
    </Stack>
  );
}

/** The patient logged in changes their password (D-86): the current one, and the new one twice. */
export function PasswordPage() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', repeated: '' });
  const [typo, setTypo] = useState<string | null>(null);
  const change = useAction(() => api.post('/api/portal/password',
    { currentPassword: form.currentPassword, newPassword: form.newPassword }), {
    refusals: OWN_PASSWORD_REFUSALS,
    done: 'Tu contraseña quedó cambiada.',
    onDone: () => setForm({ currentPassword: '', newPassword: '', repeated: '' }),
  });
  const set = (key: keyof typeof form) => (event: { currentTarget: { value: string } }) => {
    const { value } = event.currentTarget;
    setForm((before) => ({ ...before, [key]: value }));
  };

  function submit(event: FormEvent) {
    event.preventDefault();
    const wrong = newMismatch(form.newPassword, form.repeated);
    setTypo(wrong);
    if (!wrong) {
      change.run(undefined);
    }
  }

  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>Cambiar tu contraseña</Title>
      <Said error={typo ?? change.error} notice={typo ? null : change.notice} />
      <Paper className="card" p={18}>
        <form onSubmit={submit}>
          <Stack gap={16}>
            <PasswordInput label="Contraseña actual" autoComplete="current-password" required size="md"
              value={form.currentPassword} onChange={set('currentPassword')} />
            <PasswordInput label="Contraseña nueva" autoComplete="new-password" required minLength={10} size="md"
              value={form.newPassword} onChange={set('newPassword')} />
            <PasswordInput label="Repetila" autoComplete="new-password" required minLength={10} size="md"
              value={form.repeated} onChange={set('repeated')} />
            <Button type="submit" size="md" loading={change.pending}>Cambiar</Button>
          </Stack>
        </form>
      </Paper>
      <Text c="dimmed" fz={14}>De 10 a 72 caracteres, distinta de la actual.</Text>
    </Stack>
  );
}
