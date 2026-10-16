import { Anchor, Button, Group, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { NAME_REFUSALS, type PractitionerSetup } from './practitioners';

/** The practitioners, with how much of each is set up, and a new one registered (D-28). */
export function PractitionersPage() {
  const navigate = useNavigate();
  const practitioners = useQuery({ queryKey: ['practitioners'], queryFn: () => api.get<PractitionerSetup[]>('/api/practitioners') });
  const [fullName, setFullName] = useState('');
  const register = useAction(() => api.post<PractitionerSetup>('/api/practitioners', { fullName: fullName.trim() }), {
    refusals: NAME_REFUSALS, invalidates: ['practitioners'],
    onDone: (practitioner) => navigate(`/practitioners/${practitioner.id}`,
      { state: { notice: 'Profesional registrado. Cargá sus razones y su semana.' } }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    register.run(undefined);
  }

  return (
    <Stack gap={20} maw={880}>
      <Title order={1} fz={28}>Profesionales</Title>
      {practitioners.data?.length === 0 && <Text c="dimmed">Todavía no hay profesionales cargados.</Text>}
      {practitioners.data && practitioners.data.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Nombre</Table.Th><Table.Th>Razones</Table.Th><Table.Th>Tramos por semana</Table.Th>
              <Table.Th /></Table.Tr></Table.Thead>
            <Table.Tbody>
              {practitioners.data.map((practitioner) => (
                <Table.Tr key={practitioner.id}>
                  <Table.Td>{practitioner.fullName}</Table.Td>
                  <Table.Td className="mono">{practitioner.reasons.length}</Table.Td>
                  <Table.Td className="mono">{practitioner.weeklySchedule.length}</Table.Td>
                  <Table.Td>
                    <Anchor component={Link} to={`/practitioners/${practitioner.id}`}
                      aria-label={`Configurar a ${practitioner.fullName}`}>Configurar</Anchor>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
      <Title order={2} fz={20}>Registrar profesional</Title>
      <Paper className="card" p={20}>
        <form onSubmit={submit}>
          <Group align="flex-end">
            <TextInput label="Nombre completo" required maxLength={150} placeholder="Dra. Laura Arias" value={fullName}
              onChange={(event) => setFullName(event.currentTarget.value)} />
            <Button type="submit" loading={register.pending}>Registrar</Button>
          </Group>
          <Said error={register.error} />
        </form>
      </Paper>
    </Stack>
  );
}
