import { Button, PasswordInput, Stack } from '@mantine/core';
import { type FormEvent, useState } from 'react';

import { Said } from '../../shared/Said';
import { mismatch } from './account';

/**
 * A new password, typed twice so a typo does not lock the patient out (D-79, D-86): the activation's and the
 * recovery's. What was typed stays on a refusal.
 */
export function NewPasswordForm({ label, action, pending, error, onSend }: {
  label: string;
  action: string;
  pending: boolean;
  error: string | null;
  onSend: (password: string) => void;
}) {
  const [password, setPassword] = useState('');
  const [repeated, setRepeated] = useState('');
  const [typo, setTypo] = useState<string | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    const wrong = mismatch(password, repeated);
    setTypo(wrong);
    if (!wrong) {
      onSend(password);
    }
  }

  return (
    <form onSubmit={submit}>
      <Stack gap={16}>
        <Said error={typo ?? error} />
        <PasswordInput label={label} autoComplete="new-password" required minLength={10} size="md" value={password}
          onChange={(event) => setPassword(event.currentTarget.value)} />
        <PasswordInput label="Repetila" autoComplete="new-password" required minLength={10} size="md" value={repeated}
          onChange={(event) => setRepeated(event.currentTarget.value)} />
        <Button type="submit" size="md" loading={pending}>{action}</Button>
      </Stack>
    </form>
  );
}
