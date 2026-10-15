import { Alert, Anchor, Button, Group, Loader, Paper, PasswordInput, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';

import { when } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { ROLE_NAMES } from '../navigation';
import { api } from '../session';
import type { Practitioner } from '../types';
import { PASSWORD_MIN_LENGTH, type User, type UserChange, USER_CHANGES, USER_REFUSALS, userState } from './admin';

/**
 * A user (D-68): deactivated — they lose access at once, their open sessions too — or reactivated; their password
 * reset to a temporary one (D-69); and what was done to them, by whom and when.
 */
export function UserPage() {
  const { id } = useParams();
  const location = useLocation();
  const created = (location.state as { notice?: string } | null)?.notice ?? null;
  const user = useQuery({ queryKey: ['users', id], queryFn: () => api.get<User>(`/api/users/${id}`) });
  const changes = useQuery({ queryKey: ['users', id, 'changes'], queryFn: () => api.get<UserChange[]>(`/api/users/${id}/changes`) });
  const practitioners = useQuery({ queryKey: ['practitioners'], queryFn: () => api.get<Practitioner[]>('/api/practitioners'),
    enabled: Boolean(user.data?.practitionerId) });
  const [password, setPassword] = useState('');
  const access = useAction((action: 'deactivate' | 'reactivate') => api.post<User>(`/api/users/${id}/${action}`), {
    refusals: USER_REFUSALS, invalidates: ['users'],
    done: (changed) => (changed.active ? 'Usuario reactivado.' : 'Usuario desactivado: ya no puede ingresar.'),
  });
  const reset = useAction(() => api.post<User>(`/api/users/${id}/password-reset`, { password }), {
    refusals: USER_REFUSALS, invalidates: ['users'], done: 'Contraseña blanqueada. Pasásela al usuario.',
    onDone: () => setPassword(''),
  });

  if (user.isPending) {
    return <Loader aria-label="Cargando el usuario" />;
  }
  if (user.isError) {
    return <Alert color="red" role="alert">{describeError(user.error, USER_REFUSALS)}</Alert>;
  }
  const practitioner = user.data.practitionerId
    ? practitioners.data?.find((each) => each.id === user.data.practitionerId)?.fullName ?? null : null;

  function submit(event: FormEvent) {
    event.preventDefault();
    reset.run(undefined);
  }

  return (
    <Stack gap={20} maw={880}>
      <Stack gap={4}>
        <Anchor component={Link} to="/users" fz={14}>‹ Usuarios</Anchor>
        <Title order={1} fz={28}>{user.data.username}</Title>
        <Text c="dimmed">
          {[ROLE_NAMES[user.data.role], practitioner, userState(user.data)].filter(Boolean).join(' · ')}
        </Text>
      </Stack>
      <Said error={access.error ?? reset.error} notice={access.notice ?? reset.notice ?? created} />

      <Title order={2} fz={20}>Acceso</Title>
      <Paper className="card" p={20}>
        {user.data.active ? (
          <Group>
            <Button color="red" variant="light" loading={access.pending} onClick={() => access.run('deactivate')}>Desactivar</Button>
            <Text c="dimmed" fz={14}>Deja de poder ingresar en el acto, y se cierran sus sesiones abiertas.</Text>
          </Group>
        ) : (
          <Button variant="default" loading={access.pending} onClick={() => access.run('reactivate')}>Reactivar</Button>
        )}
      </Paper>

      <Title order={2} fz={20}>Blanquear la contraseña</Title>
      <Paper className="card" p={20}>
        <form onSubmit={submit}>
          <Group align="flex-end">
            <PasswordInput label={`Contraseña nueva (${PASSWORD_MIN_LENGTH} caracteres o más)`} required
              autoComplete="new-password" value={password} onChange={(event) => setPassword(event.currentTarget.value)} />
            <Button type="submit" loading={reset.pending}>Blanquear</Button>
          </Group>
        </form>
      </Paper>

      <Title order={2} fz={20}>Historial</Title>
      {changes.data?.length === 0 && <Text c="dimmed">Sin acciones registradas.</Text>}
      {changes.data && changes.data.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Cuándo</Table.Th><Table.Th>Quién</Table.Th><Table.Th>Qué</Table.Th></Table.Tr></Table.Thead>
            <Table.Tbody>
              {changes.data.map((change, index) => (
                <Table.Tr key={index}>
                  <Table.Td className="mono">{when(change.changedAt)}</Table.Td>
                  <Table.Td>{change.changedBy}</Table.Td>
                  <Table.Td>{USER_CHANGES[change.action]}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
    </Stack>
  );
}
