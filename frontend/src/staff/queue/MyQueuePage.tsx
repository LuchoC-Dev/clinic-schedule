import { Alert, Anchor, Button, Group, Loader, Modal, Stack, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import { Link } from 'react-router';

import { ApiError } from '../../shared/api';
import { timeOf } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { patientLabel } from '../arrivals/arrivals';
import { useLiveQueues } from '../arrivals/useLiveQueues';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { RecordPanel } from '../record/RecordPanel';
import { api } from '../session';
import { type MyQueue, QUEUE_REFUSALS, queueActions, waitingNote } from './queue';
import './queue.css';

type Step = 'finish' | 'did-not-come-in';

/**
 * The practitioner's queue and the record of whom they are seeing, side by side, as in the approved mock (D-63,
 * D-104): calling the next — the queue's order, never their choice —, finishing with them or saying they did not
 * come in; and, beside it, the patient's record with the new consultation. Shown again as the queue changes (D-66).
 */
export function MyQueuePage() {
  useLiveQueues();
  const mine = useQuery({ queryKey: ['my-queue'], queryFn: () => api.get<MyQueue>('/api/my-queue') });
  const [touched, setTouched] = useState(false);
  const [asking, setAsking] = useState<Step | null>(null);
  const step = useAction((path: 'call-next' | Step) => api.post<MyQueue>(`/api/my-queue/${path}`), {
    refusals: QUEUE_REFUSALS, invalidates: ['my-queue'],
  });
  const onTouched = useCallback((now: boolean) => setTouched(now), []);

  if (mine.isPending) {
    return <Loader aria-label="Cargando tu cola" />;
  }
  if (mine.isError) {
    const none = mine.error instanceof ApiError && mine.error.body?.code === 'NO_QUEUE_SERVED';
    return (
      <Stack gap={12}>
        <Title order={1} fz={24}>Mi cola</Title>
        {none
          ? <Text c="dimmed">No atendés ninguna cola. Recepción las arma.</Text>
          : <Alert color="red" role="alert">{describeError(mine.error, QUEUE_REFUSALS)}</Alert>}
      </Stack>
    );
  }

  const queue = mine.data;
  const can = queueActions(queue);
  const called = queue.called;
  const leave = (next: Step) => (touched ? setAsking(next) : step.run(next));
  return (
    <div className="my-queue">
      <section aria-label="Mi cola" className="my-queue-side">
        <Title order={1} fz={24}>Mi cola</Title>
        <Text c="dimmed" fz={14}>{queue.queueName}</Text>
        <Said error={step.error} />
        {!queue.session.open && <Text c="dimmed">La cola está cerrada.</Text>}

        {called && (
          <div className="my-queue-called">
            <span className="my-queue-label">Atendiendo</span>
            <span className="my-queue-name">{patientLabel(called)}</span>
            <span className="my-queue-label">{waitingNote(called)} · llegó {timeOf(called.arrivedAt)}</span>
            <Group gap={8}>
              {can.finish && <Button variant="white" onClick={() => leave('finish')} loading={step.pending}>Terminar</Button>}
              {can.didNotComeIn && (
                <Button variant="white" color="red" onClick={() => leave('did-not-come-in')} disabled={step.pending}>
                  No entró
                </Button>
              )}
            </Group>
          </div>
        )}

        {queue.session.open && (
          <div className="card">
            <Group justify="space-between" className="my-queue-head">
              <Text fw={600}>Esperando</Text>
              {can.callNext && (
                <Button onClick={() => step.run('call-next')} disabled={can.nobodyWaits} loading={step.pending}>
                  Llamar al siguiente
                </Button>
              )}
            </Group>
            {queue.session.waiting.length === 0 && <Text p={16} c="dimmed">Nadie espera.</Text>}
            <ol className="my-queue-waiting">
              {queue.session.waiting.map((entry) => (
                <li key={entry.id}>
                  <Stack gap={2}>
                    <Anchor component={Link} to={`/clinical-records/${entry.patientId}`} c="inherit" fw={500}>
                      {patientLabel(entry)}
                    </Anchor>
                    <Text fz={13} c="dimmed">{waitingNote(entry)}</Text>
                  </Stack>
                  {entry.emergency
                    ? <span className="my-queue-emergency">Urgencia</span>
                    : <span className="mono my-queue-time">{timeOf(entry.arrivedAt)}</span>}
                </li>
              ))}
            </ol>
          </div>
        )}
      </section>

      <div className="my-queue-record">
        {called
          ? <RecordPanel key={called.patientId} patientId={called.patientId} onTouched={onTouched} />
          : <Text c="dimmed">Cuando llames a alguien, acá ves su historia clínica.</Text>}
      </div>

      <Modal opened={asking !== null} onClose={() => setAsking(null)} title="La consulta no está guardada">
        <Stack gap={16}>
          <Text>Escribiste una consulta que todavía no guardaste. Si seguís, se pierde.</Text>
          <Group justify="flex-end" gap={8}>
            <Button variant="default" onClick={() => setAsking(null)}>Volver a la consulta</Button>
            <Button color="red" onClick={() => {
              const next = asking;
              setAsking(null);
              if (next) step.run(next);
            }}>{asking === 'finish' ? 'Terminar igual' : 'No entró: descartarla'}</Button>
          </Group>
        </Stack>
      </Modal>
    </div>
  );
}
