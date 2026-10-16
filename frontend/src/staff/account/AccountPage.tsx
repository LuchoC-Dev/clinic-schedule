import { Alert, Anchor, Button, Group, Loader, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useParams } from 'react-router';

import { ApiError } from '../../shared/api';
import { when } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { documentOf, type Patient } from '../types';
import { ACCOUNT_CHANGES, ACCOUNT_REFUSALS, ACCOUNT_STATUS, accountChangeBy } from '../words';
import { type Account, accountActions, sentSaid } from './account';

/**
 * A patient's web account (D-79, D-86): none yet, opened with its own email, its activation sent from it; or its state,
 * the activation sent again, switched off or on, its email changed with the patient's document in sight, and its
 * history.
 */
export function AccountPage() {
  const { id } = useParams();
  const patient = useQuery({ queryKey: ['patient', id], queryFn: () => api.get<Patient>(`/api/patients/${id}`) });
  const account = useQuery({
    queryKey: ['account', id],
    queryFn: () => api.get<Account>('/api/patient-accounts', { patientId: id }).catch((error: unknown) => {
      if (error instanceof ApiError && error.status === 404) return null;
      throw error;
    }),
  });
  const history = useQuery({
    queryKey: ['account', id, 'changes'],
    queryFn: () => api.get<{ action: string; at: string; by: string | null }[]>(`/api/patient-accounts/${account.data?.id}/changes`),
    enabled: Boolean(account.data),
  });
  const [email, setEmail] = useState('');
  const [newEmail, setNewEmail] = useState('');
  const act = useAction(({ path, body }: { path: string; body?: unknown }) =>
    api.post<Account>(`/api/patient-accounts${path}`, body).then((answer) => ({ path, answer })),
  { refusals: ACCOUNT_REFUSALS, invalidates: ['account'], done: ({ path, answer }) => sentSaid(path, answer) });

  if (patient.isPending || account.isPending) {
    return <Loader aria-label="Cargando la cuenta" />;
  }
  if (patient.isError || account.isError) {
    return <Alert color="red" role="alert">{describeError(patient.error ?? account.error)}</Alert>;
  }
  const open = (event: FormEvent) => {
    event.preventDefault();
    act.run({ path: '', body: { patientId: id, email: email.trim() } });
  };
  const change = (event: FormEvent) => {
    event.preventDefault();
    act.run({ path: `/${account.data?.id}/email-change`, body: { email: newEmail.trim() } });
  };
  const can = account.data ? accountActions(account.data.status) : null;

  return (
    <Stack gap={20} maw={880}>
      <Stack gap={4}>
        <Title order={1} fz={28}>Cuenta web de <Anchor component={Link} to={`/patients/${id}`} fz="inherit">
          {patient.data.fullName}</Anchor></Title>
        <Text c="dimmed">{documentOf(patient.data) ?? 'Sin documento'}</Text>
      </Stack>
      <Said error={act.error} notice={act.notice} />
      {!account.data ? (
        <Stack>
          <Text>No tiene cuenta web. Se abre para un paciente de 13 años o más, con la identidad verificada y la fecha de
            nacimiento cargada. El email es el de la cuenta: ahí le llega el link para activarla, y no cambia su email
            de contacto.</Text>
          <Paper className="card" p={20}>
            <form onSubmit={open}>
              <Group align="flex-end">
                <TextInput label="Email de la cuenta" type="email" required maxLength={254} value={email}
                  onChange={(event) => setEmail(event.currentTarget.value)} />
                <Button type="submit" loading={act.pending}>Abrir cuenta</Button>
              </Group>
            </form>
          </Paper>
        </Stack>
      ) : (
        <Stack>
          <Text>Estado: <strong>{ACCOUNT_STATUS[account.data.status]}</strong> · Email: {account.data.email}</Text>
          <Group>
            {can?.resend && (
              <Button variant="default" onClick={() => act.run({ path: `/${account.data?.id}/activation-resend` })}>
                Reenviar link de activación
              </Button>
            )}
            {can?.deactivate && (
              <Button color="red" variant="light" onClick={() => act.run({ path: `/${account.data?.id}/deactivate` })}>
                Desactivar
              </Button>
            )}
            {can?.reactivate && (
              <Button variant="default" onClick={() => act.run({ path: `/${account.data?.id}/reactivate` })}>Reactivar</Button>
            )}
          </Group>
          <Title order={2} fz={20}>Cambiar el email de la cuenta</Title>
          <Paper className="card" p={20}>
            <form onSubmit={change}>
              <Group align="flex-end">
                <TextInput label="Email nuevo" type="email" required maxLength={254} value={newEmail}
                  onChange={(event) => setNewEmail(event.currentTarget.value)} />
                <Button type="submit" variant="default">Cambiar</Button>
              </Group>
            </form>
          </Paper>
          <Text c="dimmed" fz={14}>Cambialo solo con el documento del paciente a la vista: es por donde recupera su cuenta.</Text>
          <Title order={2} fz={20}>Historial</Title>
          <section className="card">
            <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
              <Table.Thead><Table.Tr><Table.Th>Cuándo</Table.Th><Table.Th>Qué</Table.Th><Table.Th>Quién</Table.Th></Table.Tr></Table.Thead>
              <Table.Tbody>
                {(history.data ?? []).map((entry, index) => (
                  <Table.Tr key={index}>
                    <Table.Td>{when(entry.at)}</Table.Td>
                    <Table.Td>{ACCOUNT_CHANGES[entry.action] ?? entry.action}</Table.Td>
                    <Table.Td>{accountChangeBy(entry.action, entry.by)}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </section>
        </Stack>
      )}
    </Stack>
  );
}
