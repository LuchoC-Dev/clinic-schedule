import { Alert, Anchor, Button, Group, NativeSelect, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { when } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { byCode } from '../../shared/refusals';
import { api } from '../session';
import type { Practitioner } from '../types';
import { CANCELLATION_ORIGINS, cancelledBy, REQUESTER } from '../words';

interface Cancellations {
  from: string;
  to: string;
  limitReached: boolean;
  cancellations: {
    appointmentId: string;
    startsAt: string;
    practitioner: { id: string; fullName: string | null };
    patient: { id: string; fullName: string | null; document: string | null };
    cancelledAt: string | null;
    cancelledBy: string | null;
    origin: string;
    requestedBy: string | null;
    note: string | null;
  }[];
}

const REFUSALS = byCode({ ILLEGAL_ARGUMENT: 'El primer día es posterior al último.' });

/**
 * The cancelled appointments, by the day they were cancelled (D-76): who, how and who asked, the patient and
 * reception's note, of one practitioner or everyone, between two days.
 */
export function CancellationsPage() {
  const [params, setParams] = useSearchParams();
  const query = { practitionerId: params.get('practitionerId'), from: params.get('from'), to: params.get('to') };
  const practitioners = useQuery({ queryKey: ['practitioners'], queryFn: () => api.get<Practitioner[]>('/api/practitioners') });
  const found = useQuery({ queryKey: ['cancellations', query], queryFn: () => api.get<Cancellations>('/api/cancellations', query) });
  const [practitionerId, setPractitionerId] = useState(query.practitionerId ?? '');
  const [from, setFrom] = useState(query.from ?? '');
  const [to, setTo] = useState(query.to ?? '');

  function submit(event: FormEvent) {
    event.preventDefault();
    const search = new URLSearchParams();
    if (practitionerId) search.set('practitionerId', practitionerId);
    if (from) search.set('from', from);
    if (to) search.set('to', to);
    setParams(search);
  }

  return (
    <Stack gap={20}>
      <Stack gap={4}>
        <Title order={1} fz={28}>Cancelaciones</Title>
        <Text c="dimmed">Los turnos cancelados, por el día en que se cancelaron: quién, cómo y quién lo pidió.</Text>
      </Stack>
      <Paper className="card" p={16}>
        <form onSubmit={submit}>
          <Group align="flex-end">
            <NativeSelect label="Profesional" value={practitionerId} onChange={(event) => setPractitionerId(event.currentTarget.value)}
              data={[{ value: '', label: '— todos —' },
                ...(practitioners.data ?? []).map((each) => ({ value: each.id, label: each.fullName }))]} />
            <TextInput label="Desde" type="date" value={from || found.data?.from || ''} onChange={(event) => setFrom(event.currentTarget.value)} />
            <TextInput label="Hasta" type="date" value={to || found.data?.to || ''} onChange={(event) => setTo(event.currentTarget.value)} />
            <Button type="submit">Buscar</Button>
          </Group>
        </form>
      </Paper>
      {found.isError && <Alert color="red" role="alert">{describeError(found.error, REFUSALS)}</Alert>}
      {found.data?.limitReached && (
        <Alert color="amber" role="alert">Hay más cancelaciones de las que se muestran: acotá la búsqueda para ver el resto.</Alert>
      )}
      {found.data && found.data.cancellations.length === 0 && <Text c="dimmed">No hay cancelaciones con esos datos.</Text>}
      {found.data && found.data.cancellations.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Cuándo</Table.Th><Table.Th>Usuario</Table.Th><Table.Th>Cómo</Table.Th>
              <Table.Th>Lo pidió</Table.Th><Table.Th>Turno</Table.Th><Table.Th>Profesional</Table.Th><Table.Th>Paciente</Table.Th>
              <Table.Th>Nota</Table.Th></Table.Tr></Table.Thead>
            <Table.Tbody>
              {found.data.cancellations.map((each) => (
                <Table.Tr key={each.appointmentId}>
                  <Table.Td>{each.cancelledAt ? when(each.cancelledAt) : '—'}</Table.Td>
                  <Table.Td>{cancelledBy(each.origin, each.cancelledBy)}</Table.Td>
                  <Table.Td>{CANCELLATION_ORIGINS[each.origin] ?? each.origin}</Table.Td>
                  <Table.Td>{each.requestedBy ? REQUESTER[each.requestedBy] : '—'}</Table.Td>
                  <Table.Td>{when(each.startsAt)}</Table.Td>
                  <Table.Td>{each.practitioner.fullName ?? '(profesional desconocido)'}</Table.Td>
                  <Table.Td>
                    <Anchor component={Link} to={`/patients/${each.patient.id}/appointments`}>
                      {each.patient.fullName ?? '(paciente desconocido)'}
                    </Anchor>
                  </Table.Td>
                  <Table.Td>{each.note ?? ''}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
    </Stack>
  );
}
