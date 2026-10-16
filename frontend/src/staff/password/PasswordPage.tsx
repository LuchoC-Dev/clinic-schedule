import { Button, Paper, PasswordInput, Stack, Text, Title } from '@mantine/core';
import { type FormEvent, useState } from 'react';

import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api, logOut, useUser } from '../session';
import { mismatch, PASSWORD_REFUSALS } from './password';

/**
 * Changing one's own password, for every role (D-69, D-110). Whose password is temporary sees it without the menu,
 * and reaches nothing else until it is changed. Once changed, the session ends and the user logs in again with the
 * new one, as the Thymeleaf screen did: the session held the old state of their password.
 */
export function PasswordPage() {
  const user = useUser();
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', repeated: '' });
  const [typo, setTypo] = useState<string | null>(null);
  const change = useAction(() => api.post('/api/password',
    { currentPassword: form.currentPassword, newPassword: form.newPassword }), {
    refusals: PASSWORD_REFUSALS,
    onDone: () => void logOut(api, 'changed'),
  });
  const set = (key: keyof typeof form) => (event: { currentTarget: { value: string } }) => {
    const { value } = event.currentTarget;
    setForm((before) => ({ ...before, [key]: value }));
  };

  function submit(event: FormEvent) {
    event.preventDefault();
    const wrong = mismatch(form.newPassword, form.repeated);
    setTypo(wrong);
    if (!wrong) {
      change.run(undefined);
    }
  }

  return (
    <Stack gap={20} maw={420}>
      <Title order={1} fz={28}>Cambiar la contraseña</Title>
      {user.temporaryPassword && (
        <Text c="dimmed">Tu contraseña es temporal: la puso la administración. Elegí una propia para seguir.</Text>
      )}
      <Paper className="card" p={20}>
        <form onSubmit={submit}>
          <Stack gap={14}>
            <Said error={typo ?? change.error} />
            <PasswordInput label="Contraseña actual" autoComplete="current-password" required autoFocus
              value={form.currentPassword} onChange={set('currentPassword')} />
            <PasswordInput label="Contraseña nueva (10 caracteres o más)" autoComplete="new-password" required
              value={form.newPassword} onChange={set('newPassword')} />
            <PasswordInput label="Repetí la contraseña nueva" autoComplete="new-password" required
              value={form.repeated} onChange={set('repeated')} />
            <Button type="submit" loading={change.pending}>Cambiar</Button>
          </Stack>
        </form>
      </Paper>
    </Stack>
  );
}
