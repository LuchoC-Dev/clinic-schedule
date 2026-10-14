import { Alert, Anchor, Button, Group, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { when } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { byCode } from '../../shared/refusals';
import { api } from '../session';
import { reminderOutcome } from '../words';

interface Reminders {
  from: string;
  to: string;
  reminders: {
    appointmentId: string;
    startsAt: string;
    practitioner: { id: string; fullName: string | null };
    patient: { id: string; fullName: string | null; document: string | null };
    outcome: 'SENT' | 'FAILED' | 'NOT_SENT';
    reason: string | null;
    recordedAt: string;
  }[];
}

const REFUSALS = byCode({ ILLEGAL_ARGUMENT: 'El primer día es posterior al último.' });

/**
 * What happened to each appointment's reminder by email, by the appointment's day (D-78): sent, failed — the patient
 * to call —, or not sent and why. Never the address.
 */
export function RemindersPage() {
  const [params, setParams] = useSearchParams();
  const query = { from: params.get('from'), to: params.get('to') };
  const found = useQuery({ queryKey: ['reminders', query], queryFn: () => api.get<Reminders>('/api/reminders', query) });
  const [from, setFrom] = useState(query.from ?? '');
  const [to, setTo] = useState(query.to ?? '');

  function submit(event: FormEvent) {
    event.preventDefault();
    const search = new URLSearchParams();
    if (from) search.set('from', from);
    if (to) search.set('to', to);
    setParams(search);
  }

  return (
    <Stack gap={20}>
      <Stack gap={4}>
        <Title order={1} fz={28}>Recordatorios</Title>
        <Text c="dimmed">Los recordatorios por email de los turnos, por el día del turno. Si uno no se pudo enviar, avisale al
          paciente por teléfono.</Text>
      </Stack>
      <Paper className="card" p={16}>
        <form onSubmit={submit}>
          <Group align="flex-end">
            <TextInput label="Desde" type="date" value={from || found.data?.from || ''} onChange={(event) => setFrom(event.currentTarget.value)} />
            <TextInput label="Hasta" type="date" value={to || found.data?.to || ''} onChange={(event) => setTo(event.currentTarget.value)} />
            <Button type="submit">Buscar</Button>
          </Group>
        </form>
      </Paper>
      {found.isError && <Alert color="red" role="alert">{describeError(found.error, REFUSALS)}</Alert>}
      {found.data && found.data.reminders.length === 0 && <Text c="dimmed">No hay recordatorios de turnos de esos días.</Text>}
      {found.data && found.data.reminders.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Turno</Table.Th><Table.Th>Profesional</Table.Th><Table.Th>Paciente</Table.Th>
              <Table.Th>Cuándo</Table.Th><Table.Th>Resultado</Table.Th></Table.Tr></Table.Thead>
            <Table.Tbody>
              {found.data.reminders.map((each) => (
                <Table.Tr key={each.appointmentId}>
                  <Table.Td>{when(each.startsAt)}</Table.Td>
                  <Table.Td>{each.practitioner.fullName ?? '(profesional desconocido)'}</Table.Td>
                  <Table.Td>
                    <Anchor component={Link} to={`/patients/${each.patient.id}`}>
                      {each.patient.fullName ? (each.patient.document ? `${each.patient.fullName} — ${each.patient.document}`
                        : each.patient.fullName) : '(paciente desconocido)'}
                    </Anchor>
                  </Table.Td>
                  <Table.Td>{when(each.recordedAt)}</Table.Td>
                  <Table.Td c={each.outcome === 'FAILED' ? 'red.8' : undefined} fw={each.outcome === 'FAILED' ? 600 : undefined}>
                    {reminderOutcome(each.outcome, each.reason)}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
    </Stack>
  );
}
