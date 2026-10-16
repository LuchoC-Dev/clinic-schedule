import { Anchor, Button, Group, NativeSelect, Paper, PasswordInput, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { ROLE_NAMES, type Role } from '../navigation';
import { api } from '../session';
import type { Practitioner } from '../types';
import { newUser, PASSWORD_MIN_LENGTH, ROLES, type User, USER_REFUSALS, userState } from './admin';

/**
 * The practice's users (D-68): every one, active or not, and a new one — a username that never changes nor is used
 * again, a role, the practitioner they stand for among those with no active user, and a temporary password (D-69).
 */
export function UsersPage() {
  const navigate = useNavigate();
  const users = useQuery({ queryKey: ['users'], queryFn: () => api.get<User[]>('/api/users') });
  const practitioners = useQuery({ queryKey: ['practitioners'], queryFn: () => api.get<Practitioner[]>('/api/practitioners') });
  const free = useQuery({ queryKey: ['users', 'practitioners-without-user'],
    queryFn: () => api.get<{ id: string; fullName: string }[]>('/api/users/practitioners-without-user') });
  const [form, setForm] = useState({ username: '', role: '' as Role | '', practitionerId: '', password: '' });
  const create = useAction(() => api.post<User>('/api/users', newUser(form)), {
    refusals: USER_REFUSALS, invalidates: ['users'],
    onDone: (user) => navigate(`/users/${user.id}`, { state: { notice: `Usuario creado: ${user.username}.` } }),
  });
  const names = new Map((practitioners.data ?? []).map((each) => [each.id, each.fullName]));
  const set = (key: keyof typeof form) => (event: { currentTarget: { value: string } }) => {
    const { value } = event.currentTarget;
    setForm((before) => ({ ...before, [key]: value }));
  };

  function submit(event: FormEvent) {
    event.preventDefault();
    create.run(undefined);
  }

  return (
    <Stack gap={20} maw={960}>
      <Title order={1} fz={28}>Usuarios</Title>

      <Title order={2} fz={20}>Nuevo usuario</Title>
      <Paper className="card" p={20}>
        <form onSubmit={submit}>
          <Group align="flex-end">
            <TextInput label="Usuario" required maxLength={50} autoComplete="off" value={form.username} onChange={set('username')} />
            <NativeSelect label="Rol" required value={form.role} onChange={set('role')}
              data={[{ value: '', label: '— elegí —' }, ...ROLES.map((role) => ({ value: role, label: ROLE_NAMES[role] }))]} />
            {form.role === 'PRACTITIONER' && (
              <NativeSelect label="Profesional" required value={form.practitionerId} onChange={set('practitionerId')}
                data={[{ value: '', label: '— elegí —' },
                  ...(free.data ?? []).map((each) => ({ value: each.id, label: each.fullName }))]} />
            )}
            <PasswordInput label={`Contraseña (${PASSWORD_MIN_LENGTH} caracteres o más)`} required autoComplete="new-password"
              value={form.password} onChange={set('password')} />
            <Button type="submit" loading={create.pending}>Crear</Button>
          </Group>
          <Said error={create.error} />
        </form>
      </Paper>
      <Text c="dimmed" fz={14}>Un nombre de usuario no se puede cambiar ni volver a usar. Un profesional tiene un solo usuario
        activo. La contraseña es temporal: el usuario la cambia al ingresar.</Text>

      <Title order={2} fz={20}>Todos</Title>
      {users.data && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Usuario</Table.Th><Table.Th>Rol</Table.Th><Table.Th>Profesional</Table.Th>
              <Table.Th>Estado</Table.Th></Table.Tr></Table.Thead>
            <Table.Tbody>
              {users.data.map((user) => (
                <Table.Tr key={user.id} c={user.active ? undefined : 'dimmed'}>
                  <Table.Td><Anchor component={Link} to={`/users/${user.id}`}>{user.username}</Anchor></Table.Td>
                  <Table.Td>{ROLE_NAMES[user.role]}</Table.Td>
                  <Table.Td>{user.practitionerId ? names.get(user.practitionerId) ?? '(profesional desconocido)' : '—'}</Table.Td>
                  <Table.Td>{userState(user)}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
    </Stack>
  );
}
