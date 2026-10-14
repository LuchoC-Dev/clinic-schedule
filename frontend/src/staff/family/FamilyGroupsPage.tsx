import { Anchor, Button, Group, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';

import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import type { Patient } from '../types';
import type { FamilyGroup } from './family';

/** The family groups by name, or a patient's (D-84); and a new one, formed at the desk. */
export function FamilyGroupsPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const patientId = params.get('patient');
  const patient = useQuery({ queryKey: ['patient', patientId], queryFn: () => api.get<Patient>(`/api/patients/${patientId}`),
    enabled: Boolean(patientId) });
  const groups = useQuery({ queryKey: ['family-groups', patientId],
    queryFn: () => api.get<FamilyGroup[]>('/api/family-groups', { patientId }) });
  const [name, setName] = useState('');
  const create = useAction(() => api.post<FamilyGroup>('/api/family-groups', { name: name.trim() }), {
    refusals: () => 'El grupo necesita un nombre de hasta 100 caracteres.', invalidates: ['family-groups'],
    onDone: (group) => navigate(`/family-groups/${group.id}`, { state: { notice: 'Grupo creado. Agregá a sus miembros.' } }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    create.run(undefined);
  }

  return (
    <Stack gap={20} maw={880}>
      <Title order={1} fz={28}>
        {patient.data ? <>Grupos familiares de <Anchor component={Link} to={`/patients/${patient.data.id}`} fz="inherit">
          {patient.data.fullName}</Anchor></> : 'Grupos familiares'}
      </Title>
      {groups.data?.length === 0 && <Text c="dimmed">No hay grupos.</Text>}
      {groups.data && groups.data.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Grupo</Table.Th><Table.Th>Miembros</Table.Th></Table.Tr></Table.Thead>
            <Table.Tbody>
              {groups.data.map((group) => (
                <Table.Tr key={group.id}>
                  <Table.Td><Anchor component={Link} to={`/family-groups/${group.id}`}>{group.name}</Anchor></Table.Td>
                  <Table.Td className="mono">{group.members.length}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
      <Title order={2} fz={20}>Nuevo grupo</Title>
      <Paper className="card" p={20}>
        <form onSubmit={submit}>
          <Group align="flex-end">
            <TextInput label="Nombre" required maxLength={100} placeholder="Familia Pérez" value={name}
              onChange={(event) => setName(event.currentTarget.value)} />
            <Button type="submit" loading={create.pending}>Crear grupo</Button>
          </Group>
          <Said error={create.error} />
        </form>
      </Paper>
      <Text c="dimmed" fz={14}>Después se agregan los miembros: quien tiene 13 años o más entra presente y aceptando; un
        menor de 13, a pedido de su madre, padre o tutor del grupo, con la documentación a la vista.</Text>
    </Stack>
  );
}
