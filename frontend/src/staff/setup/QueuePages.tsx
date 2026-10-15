import { Alert, Anchor, Button, Group, Loader, NativeSelect, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';

import { describeError } from '../../shared/errors';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import type { Practitioner } from '../types';
import { available, type Queue, QUEUE_REFUSALS, queueBody, servedBy } from './queues';

function usePractitioners(): Practitioner[] {
  return useQuery({ queryKey: ['practitioners'], queryFn: () => api.get<Practitioner[]>('/api/practitioners') }).data ?? [];
}

/** Every queue with who serves it, and a new one set up (D-62). */
export function QueuesPage() {
  const navigate = useNavigate();
  const queues = useQuery({ queryKey: ['queues'], queryFn: () => api.get<Queue[]>('/api/queues') });
  const practitioners = usePractitioners();
  const [form, setForm] = useState({ name: '', room: '' });
  const create = useAction(() => api.post<Queue>('/api/queues', queueBody(form)), {
    refusals: QUEUE_REFUSALS, invalidates: ['queues'],
    onDone: (queue) => navigate(`/queues/${queue.id}`, { state: { notice: 'Cola creada.' } }),
  });
  function submit(event: FormEvent) {
    event.preventDefault();
    create.run(undefined);
  }
  return (
    <Stack gap={20} maw={880}>
      <Title order={1} fz={28}>Colas</Title>
      {queues.data?.length === 0 && <Text c="dimmed">Todavía no hay colas.</Text>}
      {queues.data && queues.data.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Cola</Table.Th><Table.Th>Consultorio</Table.Th><Table.Th>Profesionales</Table.Th>
              <Table.Th /></Table.Tr></Table.Thead>
            <Table.Tbody>
              {queues.data.map((queue) => (
                <Table.Tr key={queue.id}>
                  <Table.Td>{queue.name}</Table.Td>
                  <Table.Td>{queue.room ?? '—'}</Table.Td>
                  <Table.Td>
                    {queue.practitionerIds.length === 0
                      ? <Text span c="dimmed">Nadie la atiende</Text> : servedBy(queue, practitioners).join(', ')}
                  </Table.Td>
                  <Table.Td>
                    <Anchor component={Link} to={`/queues/${queue.id}`} aria-label={`Editar ${queue.name}`}>Editar</Anchor>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
      <Title order={2} fz={20}>Nueva cola</Title>
      <Paper className="card" p={20}>
        <form onSubmit={submit}>
          <Group align="flex-end">
            <TextInput label="Nombre" required maxLength={60} placeholder="Consultorio 3, Guardia…" value={form.name}
              onChange={(event) => { const { value } = event.currentTarget; setForm((before) => ({ ...before, name: value })); }} />
            <TextInput label="Consultorio" maxLength={20} placeholder="opcional" value={form.room}
              onChange={(event) => { const { value } = event.currentTarget; setForm((before) => ({ ...before, room: value })); }} />
            <Button type="submit" loading={create.pending}>Crear</Button>
          </Group>
          <Said error={create.error} />
        </form>
      </Paper>
    </Stack>
  );
}

/** A queue: renamed, and its practitioners added or taken out — one who serves another queue is not offered (D-62). */
export function QueuePage() {
  const { id } = useParams();
  const location = useLocation();
  const created = (location.state as { notice?: string } | null)?.notice ?? null;
  const queue = useQuery({ queryKey: ['queues', id], queryFn: () => api.get<Queue>(`/api/queues/${id}`) });
  const queues = useQuery({ queryKey: ['queues'], queryFn: () => api.get<Queue[]>('/api/queues') });
  const practitioners = usePractitioners();
  const [chosen, setChosen] = useState('');
  // Each change of the queue, sent once; what it says once done comes with it.
  const act = useAction(async (change: { send: () => Promise<unknown>; done: string }) => {
    await change.send();
    return change.done;
  }, { refusals: QUEUE_REFUSALS, invalidates: ['queues'], done: (said) => said });
  const run = (send: () => Promise<unknown>, done: string) => act.run({ send, done });

  if (queue.isPending) {
    return <Loader aria-label="Cargando la cola" />;
  }
  if (queue.isError) {
    return <Alert color="red" role="alert">{describeError(queue.error, QUEUE_REFUSALS)}</Alert>;
  }
  const offered = available(practitioners, queues.data ?? []);
  const names = servedBy(queue.data, practitioners);

  function add(event: FormEvent) {
    event.preventDefault();
    const practitionerId = chosen || offered[0]?.id;
    run(() => api.post(`/api/queues/${id}/practitioners`, { practitionerId }), 'Profesional agregado a la cola.');
  }

  return (
    <Stack gap={20} maw={880}>
      <Stack gap={4}>
        <Anchor component={Link} to="/queues" fz={14}>‹ Colas</Anchor>
        <Title order={1} fz={28}>{queue.data.name}</Title>
        {queue.data.room && <Text c="dimmed">Consultorio {queue.data.room}</Text>}
      </Stack>
      <Said error={act.error} notice={act.notice ?? created} />

      <Title order={2} fz={20}>Datos</Title>
      <QueueForm key={queue.data.id} queue={queue.data}
        onSave={(body) => run(() => api.put(`/api/queues/${id}`, body), 'Cola guardada.')} pending={act.pending} />

      <Title order={2} fz={20}>Profesionales</Title>
      {queue.data.practitionerIds.length === 0 && <Text c="dimmed">Nadie atiende esta cola.</Text>}
      {queue.data.practitionerIds.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Tbody>
              {queue.data.practitionerIds.map((practitionerId, index) => (
                <Table.Tr key={practitionerId}>
                  <Table.Td>{names[index]}</Table.Td>
                  <Table.Td>
                    <Button color="red" variant="light" size="xs" aria-label={`Quitar a ${names[index]}`}
                      onClick={() => run(() => api.delete(`/api/queues/${id}/practitioners/${practitionerId}`),
                        'Profesional quitado de la cola.')}>
                      Quitar
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
      {offered.length > 0 ? (
        <Paper className="card" p={20}>
          <form onSubmit={add}>
            <Group align="flex-end">
              <NativeSelect label="Agregar profesional" value={chosen || offered[0].id}
                onChange={(event) => setChosen(event.currentTarget.value)}
                data={offered.map((each) => ({ value: each.id, label: each.fullName }))} />
              <Button type="submit" variant="default" loading={act.pending}>Agregar</Button>
            </Group>
          </form>
        </Paper>
      ) : <Text c="dimmed">Todos los profesionales ya atienden una cola.</Text>}
    </Stack>
  );
}

function QueueForm({ queue, onSave, pending }: {
  queue: Queue;
  onSave: (body: ReturnType<typeof queueBody>) => void;
  pending: boolean;
}) {
  const [form, setForm] = useState({ name: queue.name, room: queue.room ?? '' });
  function submit(event: FormEvent) {
    event.preventDefault();
    onSave(queueBody(form));
  }
  return (
    <Paper className="card" p={20}>
      <form onSubmit={submit}>
        <Group align="flex-end">
          <TextInput label="Nombre" required maxLength={60} value={form.name}
            onChange={(event) => { const { value } = event.currentTarget; setForm((before) => ({ ...before, name: value })); }} />
          <TextInput label="Consultorio" maxLength={20} placeholder="opcional" value={form.room}
            onChange={(event) => { const { value } = event.currentTarget; setForm((before) => ({ ...before, room: value })); }} />
          <Button type="submit" loading={pending}>Guardar</Button>
        </Group>
      </form>
    </Paper>
  );
}
