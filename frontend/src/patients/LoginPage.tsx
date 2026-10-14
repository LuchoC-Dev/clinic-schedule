import { Alert, Anchor, Button, NativeSelect, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { type FormEvent, useContext, useState } from 'react';
import { Navigate, useSearchParams } from 'react-router';

import { describeError } from '../shared/errors';
import { SessionContext, SetSessionContext, logIn } from './session';

/**
 * Every failure says the same, as on the Thymeleaf login: saying that the account is locked would tell anyone
 * that the document has one (D-86).
 */
export const LOGIN_FAILED = 'No pudimos ingresarte: revisá el documento y la contraseña. Después de 5 intentos '
  + 'fallidos la cuenta se bloquea 15 minutos; si no la recordás, recuperala.';

const ENDED: Record<string, string> = {
  logout: 'Sesión cerrada.',
  expired: 'Tu sesión se cerró: tu cuenta fue desactivada o cambió su contraseña.',
};

/**
 * The patients' login, phone first (D-80, D-97): the type of document chosen from a list, the number typed apart,
 * and the password. A refusal keeps the document typed.
 */
export function LoginPage() {
  const session = useContext(SessionContext);
  const setSession = useContext(SetSessionContext);
  const [params] = useSearchParams();
  const [documentType, setDocumentType] = useState(session.documentTypes[0] ?? 'DNI');
  const [documentNumber, setDocumentNumber] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  if (session.loggedIn) {
    return <Navigate to="/" replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSending(true);
    setError(null);
    try {
      setSession(await logIn(documentType, documentNumber, password));
    } catch (e) {
      setPassword('');
      setError(describeError(e, (refusal) => (refusal.status === 401 ? LOGIN_FAILED : undefined)));
    } finally {
      setSending(false);
    }
  }

  const ended = ENDED[[...params.keys()].find((key) => key in ENDED) ?? ''];
  return (
    <main className="patients-main">
      <form onSubmit={submit}>
        <Stack gap={16}>
          <Title order={1} fz={26}>Ingresá</Title>
          <Text c="dimmed">Tus turnos, con tu documento y tu contraseña.</Text>
          {ended && !error && <Alert color="petrol">{ended}</Alert>}
          {error && <Alert color="red" role="alert">{error}</Alert>}
          <NativeSelect label="Tipo de documento" data={session.documentTypes} value={documentType}
            onChange={(event) => setDocumentType(event.currentTarget.value)} size="md" />
          <TextInput label="Número" autoComplete="username" required maxLength={20} value={documentNumber}
            onChange={(event) => setDocumentNumber(event.currentTarget.value)} size="md" inputMode="text" />
          <PasswordInput label="Contraseña" autoComplete="current-password" required value={password}
            onChange={(event) => setPassword(event.currentTarget.value)} size="md" />
          <Button type="submit" size="md" loading={sending}>Ingresar</Button>
          <Anchor href="/portal/password-recovery">¿Olvidaste tu contraseña?</Anchor>
          <Text fz={15}>¿No tenés cuenta? <Anchor href="/portal/register">Registrate</Anchor>, o pedila en recepción.</Text>
        </Stack>
      </form>
    </main>
  );
}
