import { Alert, Button, Center, Paper, PasswordInput, Stack, TextInput } from '@mantine/core';
import { type FormEvent, useContext, useState } from 'react';
import { useSearchParams } from 'react-router';

import { ApiError } from '../shared/api';
import { Brand } from '../shared/Brand';
import { describeError } from '../shared/errors';
import { GoTo } from './GoTo';
import { landingFor } from './navigation';
import { SessionContext, SetUserContext, logIn } from './session';

/** What the login says of how the last session ended, as the Thymeleaf login does. */
const ENDED: Record<string, string> = {
  logout: 'Sesión cerrada.',
  changed: 'Contraseña cambiada: ingresá con la nueva.',
  expired: 'Tu sesión se cerró: volvé a ingresar. Si tu usuario fue desactivado, no vas a poder.',
};

/**
 * The staff's login (D-97): a username and a password, sent to the screens' own login. A wrong one says so
 * and keeps the username typed.
 */
export function LoginPage() {
  const user = useContext(SessionContext);
  const setUser = useContext(SetUserContext);
  const [params] = useSearchParams();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  if (user) {
    return <GoTo entry={landingFor(user)} />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSending(true);
    setError(null);
    try {
      setUser(await logIn(username, password));
    } catch (e) {
      setPassword('');
      setError(describeError(e, (refusal: ApiError) =>
        refusal.status === 401 ? 'Usuario o contraseña incorrectos.' : undefined));
    } finally {
      setSending(false);
    }
  }

  const ended = ENDED[[...params.keys()].find((key) => key in ENDED) ?? ''];
  return (
    <Center mih="100vh" p={16}>
      <Paper className="card" p={28} w={380} maw="100%">
        <form onSubmit={submit}>
          <Stack gap={16}>
            <Brand size={20} />
            {ended && !error && <Alert color="petrol">{ended}</Alert>}
            {error && <Alert color="red" role="alert">{error}</Alert>}
            <TextInput label="Usuario" autoComplete="username" required autoFocus value={username}
              onChange={(event) => setUsername(event.currentTarget.value)} />
            <PasswordInput label="Contraseña" autoComplete="current-password" required value={password}
              onChange={(event) => setPassword(event.currentTarget.value)} />
            <Button type="submit" loading={sending}>Ingresar</Button>
          </Stack>
        </form>
      </Paper>
    </Center>
  );
}
