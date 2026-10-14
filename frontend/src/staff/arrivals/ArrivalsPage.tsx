import { Alert, Anchor, Button, Group, Loader, NativeSelect, Paper, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import { timeOf } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import type { Agenda } from '../agenda/agenda';
import { type Charge, ChargeDialog } from '../components/ChargeDialog';
import { SearchBox, usePatientSearch } from '../components/PatientSearch';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import { documentOf, type Patient, type Practitioner } from '../types';
import { ARRIVAL_REFUSALS, ENTRY_STATES } from '../words';
import { appointmentsToday, type Entry, givableTo, patientLabel, type Queue, type Session, timesCalledText } from './arrivals';
import { useLiveQueues } from './useLiveQueues';

const QUEUE_DONE: Record<string, string> = {
  open: 'Cola abierta.',
  close: 'Cola cerrada.',
  'auto-close-off': 'La cola ya no se cierra sola al terminar el día.',
};

const ENTRY_DONE: Record<string, string> = {
  reassign: 'Paciente asignado a otro profesional, en el mismo lugar.',
  'lift-reservation': 'Cualquier profesional de la cola puede llamarlo.',
  emergency: 'Marcado como urgencia.',
  left: 'Marcado como que se fue sin ser atendido.',
};

/** Charging what comes in, as its dialog asks for it. */
interface Charging {
  title: string;
  done: string;
  about: string;
  button: string;
  emergency: boolean;
  withCoverage: boolean;
  send: (charge: Charge) => Promise<unknown>;
}

/**
 * The arrivals (D-63, D-64, D-66, D-87): a queue opened and closed; who waits, in the order they will be called, given
 * to another practitioner, freed of whom they wait for, made an emergency, or gone; who was called and who is done; and
 * who comes in — with their appointment of today, without one, as an emergency, or with no name — each visit charged
 * as it comes in. Shown again whenever a queue changes, from any screen.
 */
export function ArrivalsPage() {
  useLiveQueues();
  const [params, setParams] = useSearchParams();
  const queues = useQuery({ queryKey: ['queues'], queryFn: () => api.get<Queue[]>('/api/queues') });
  const practitioners = useQuery({ queryKey: ['practitioners'], queryFn: () => api.get<Practitioner[]>('/api/practitioners') });
  const queue = queues.data?.find((each) => each.id === params.get('queueId')) ?? queues.data?.[0];
  const session = useQuery({
    queryKey: ['session', queue?.id],
    queryFn: () => api.get<Session>(`/api/queues/${queue?.id}/session`),
    enabled: Boolean(queue),
  });
  const today = useQuery({ queryKey: ['agenda', null, null, false], queryFn: () => api.get<Agenda>('/api/agenda', {}) });
  const [query, setQuery] = useState('');
  const found = usePatientSearch(query);
  const [charging, setCharging] = useState<Charging | null>(null);
  const refresh = ['session', 'agenda'];
  const [charged, setCharged] = useState<string | null>(null);
  const queueAction = useAction(async (action: string) => {
    await api.post(`/api/queues/${queue?.id}/${action}`);
    return action;
  }, { refusals: ARRIVAL_REFUSALS, invalidates: refresh, done: (action) => QUEUE_DONE[action] });
  const entryAction = useAction(async ({ entry, action, body }: { entry: Entry; action: string; body?: unknown }) => {
    await api.post(`/api/queue-entries/${entry.id}/${action}`, body);
    return action;
  }, { refusals: ARRIVAL_REFUSALS, invalidates: refresh, done: (action) => ENTRY_DONE[action] });

  if (queues.isPending) {
    return <Loader aria-label="Cargando las colas" />;
  }
  if (queues.isError) {
    return <Alert color="red" role="alert">{describeError(queues.error)}</Alert>;
  }
  if (!queue) {
    return (
      <Stack>
        <Title order={1} fz={28}>Llegadas</Title>
        <Text c="dimmed">Todavía no hay colas: <Anchor href="/queues">creá una</Anchor>.</Text>
      </Stack>
    );
  }
  const open = session.data?.open ?? false;
  const everyone = practitioners.data ?? [];
  const charge = (patient: Patient) => (title: string, button: string, emergency: boolean, appointmentId?: string) => {
    setCharging({
      title, button, emergency, withCoverage: true,
      done: appointmentId ? 'Paciente con turno sumado a la cola de su profesional.'
        : emergency ? 'Paciente sumado como urgencia.' : 'Paciente sumado a la cola.',
      about: documentOf(patient) ? `${patient.fullName} — ${documentOf(patient)}` : patient.fullName,
      send: (body) => appointmentId
        ? api.post('/api/appointment-arrivals', { appointmentId, ...body })
        : api.post(`/api/queues/${queue.id}/session/patients`, { patientId: patient.id, emergency, ...body }),
    });
  };
  const unidentified = (emergency: boolean) => setCharging({
    title: emergency ? 'Urgencia sin datos (NN)' : 'Paciente sin datos (NN)', button: 'Registrar y sumar',
    done: 'Paciente sin datos registrado y sumado a la cola.',
    about: 'Se lo registra con un alias, para completar sus datos después.', emergency, withCoverage: false,
    send: ({ amount, means }) => api.post(`/api/queues/${queue.id}/session/unidentified`, { emergency, amount, means }),
  });

  return (
    <Stack gap={20}>
      <Group justify="space-between" align="flex-end">
        <Title order={1} fz={28}>Llegadas</Title>
        <NativeSelect label="Cola" value={queue.id} onChange={(event) => setParams({ queueId: event.currentTarget.value })}
          data={(queues.data ?? []).map((each) => ({ value: each.id, label: each.name }))} />
      </Group>
      <Said error={queueAction.error ?? entryAction.error} notice={queueAction.notice ?? entryAction.notice ?? charged} />

      <Paper className="card" p={16}>
        <Group>
          <Text fw={700}>{open ? 'Abierta' : 'Cerrada'}</Text>
          {open && <Text c="dimmed" fz={14}>{session.data?.closesByItself ? 'Se cierra sola al terminar el día.'
            : 'Queda abierta hasta que la cierres.'}</Text>}
          {!open && <Button loading={queueAction.pending} onClick={() => queueAction.run('open')}>Abrir</Button>}
          {open && session.data?.closesByItself && (
            <Button variant="default" onClick={() => queueAction.run('auto-close-off')}>No cerrar al terminar el día</Button>
          )}
          {open && <Button color="red" variant="light" onClick={() => queueAction.run('close')}>Cerrar</Button>}
        </Group>
      </Paper>

      {open && session.data && (
        <>
          {/* Kept for a practitioner who no longer serves the queue, so nobody calls them (D-64). */}
          {session.data.uncallable.length > 0 && (
            <Alert color="amber" role="alert">
              Nadie puede llamar a {session.data.uncallable.map((entry) =>
                `${patientLabel(entry)} (espera a ${entry.reservedFor})`).join(', ')}: su profesional ya no atiende esta
              cola. Dáselo a otro o quitale la reserva.
            </Alert>
          )}
          <Title order={2} fz={20}>Esperando</Title>
          {session.data.waiting.length === 0 ? <Text c="dimmed">Nadie espera.</Text> : (
            <section className="card">
              <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
                <Table.Thead><Table.Tr><Table.Th>#</Table.Th><Table.Th>Paciente</Table.Th><Table.Th>Llegó</Table.Th>
                  <Table.Th /><Table.Th /></Table.Tr></Table.Thead>
                <Table.Tbody>
                  {session.data.waiting.map((entry, index) => (
                    <WaitingRow key={entry.id} position={index + 1} entry={entry}
                      givable={givableTo(entry, queue, everyone)}
                      act={(action, body) => entryAction.run({ entry, action, body })} />
                  ))}
                </Table.Tbody>
              </Table>
            </section>
          )}
          <Title order={2} fz={20}>Llamados</Title>
          {session.data.called.length === 0 ? <Text c="dimmed">Nadie está siendo atendido.</Text> : (
            <section className="card">
              <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
                <Table.Tbody>
                  {session.data.called.map((entry) => (
                    <Table.Tr key={entry.id}><Table.Td>{patientLabel(entry)}</Table.Td>
                      <Table.Td>{entry.withPractitioner}</Table.Td></Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </section>
          )}
          {session.data.done.length > 0 && (
            <>
              <Title order={2} fz={20}>Ya no esperan</Title>
              <section className="card">
                <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
                  <Table.Tbody>
                    {session.data.done.map((entry) => (
                      <Table.Tr key={entry.id}><Table.Td>{patientLabel(entry)}</Table.Td>
                        <Table.Td>{ENTRY_STATES[entry.state] ?? entry.state}</Table.Td>
                        <Table.Td>{entry.withPractitioner ?? ''}</Table.Td></Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </section>
            </>
          )}

          <Title order={2} fz={20}>Sumar a la cola</Title>
          <SearchBox onSearch={setQuery} />
          {query && found.data?.length === 0 && <Text c="dimmed">No hay pacientes que coincidan.</Text>}
          {found.data && found.data.length > 0 && (
            <section className="card">
              <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
                <Table.Tbody>
                  {found.data.map((patient) => {
                    const withAppointment = appointmentsToday(patient.id, today.data, queue);
                    const start = charge(patient);
                    return (
                      <Table.Tr key={patient.id}>
                        <Table.Td>{documentOf(patient) ? `${patient.fullName} — ${documentOf(patient)}` : patient.fullName}</Table.Td>
                        <Table.Td>
                          <Group gap={6} justify="flex-end">
                            {withAppointment.map(({ appointment, label }) => (
                              <Button key={appointment.id}
                                onClick={() => start(`Llegó con turno ${label}`, 'Llegó', false, appointment.id)}>
                                Llegó con turno {label}
                              </Button>
                            ))}
                            <Button variant={withAppointment.length === 0 ? 'filled' : 'default'}
                              onClick={() => start('Sumar a la cola', 'Sumar', false)}>
                              {withAppointment.length === 0 ? 'Sumar' : 'Sumar sin turno'}
                            </Button>
                            <Button variant="default" onClick={() => start('Sumar como urgencia', 'Sumar', true)}>
                              Sumar como urgencia
                            </Button>
                          </Group>
                        </Table.Td>
                      </Table.Tr>
                    );
                  })}
                </Table.Tbody>
              </Table>
            </section>
          )}
          {/* Nobody is turned away for lack of a name (D-60). */}
          <Paper className="card" p={16}>
            <Group justify="space-between">
              <Text c="dimmed" fz={14}>Si el paciente no da su nombre, se lo registra con un alias.</Text>
              <Group gap={6}>
                <Button variant="default" onClick={() => unidentified(false)}>Sumar sin datos (NN)</Button>
                <Button variant="default" onClick={() => unidentified(true)}>Urgencia sin datos (NN)</Button>
              </Group>
            </Group>
          </Paper>
        </>
      )}
      {charging && (
        <ChargeDialog title={charging.title} about={charging.about} button={charging.button}
          emergency={charging.emergency} withCoverage={charging.withCoverage} refusals={ARRIVAL_REFUSALS}
          invalidates={refresh} send={charging.send} onClose={() => setCharging(null)}
          onDone={() => setCharged(charging.done)} />
      )}
    </Stack>
  );
}

function WaitingRow({ position, entry, givable, act }: {
  position: number;
  entry: Entry;
  givable: { id: string; fullName: string }[];
  act: (action: string, body?: unknown) => void;
}) {
  const [to, setTo] = useState(givable[0]?.id ?? '');
  const called = timesCalledText(entry.timesCalled);
  return (
    <Table.Tr>
      <Table.Td className="mono">{position}</Table.Td>
      <Table.Td>{patientLabel(entry)}</Table.Td>
      <Table.Td className="mono">{timeOf(entry.arrivedAt)}</Table.Td>
      <Table.Td>
        {entry.emergency && <Text span fw={700} c="red.8">Urgencia </Text>}
        {entry.appointmentAt && <Text span>Turno {timeOf(entry.appointmentAt)} </Text>}
        {entry.reservedFor && <Text span c="dimmed">· espera a {entry.reservedFor} </Text>}
        {called && <Text span c="dimmed">{called}</Text>}
      </Table.Td>
      <Table.Td>
        <Group gap={6} justify="flex-end" wrap="nowrap">
          {!entry.emergency && <Button variant="default" size="xs" onClick={() => act('emergency')}>Urgencia</Button>}
          {givable.length > 0 && (
            <>
              <NativeSelect size="xs" aria-label={`Dar a ${entry.patient ?? ''}`} value={to}
                onChange={(event) => setTo(event.currentTarget.value)}
                data={givable.map((each) => ({ value: each.id, label: each.fullName }))} />
              <Button variant="default" size="xs" onClick={() => act('reassign', { practitionerId: to })}>Dar a</Button>
            </>
          )}
          {entry.reservedFor && (
            <Button variant="default" size="xs" onClick={() => act('lift-reservation')}>Quitar reserva</Button>
          )}
          <Button color="red" variant="light" size="xs" onClick={() => act('left')}>Se fue</Button>
        </Group>
      </Table.Td>
    </Table.Tr>
  );
}
