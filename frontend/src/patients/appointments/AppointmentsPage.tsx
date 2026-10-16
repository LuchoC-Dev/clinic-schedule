import { Button, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { Link, useLocation } from 'react-router';

import { describeError } from '../../shared/errors';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { dayAndTime } from '../words';
import './appointments.css';
import { CANCEL_REFUSALS, type Entry, type OwnAppointments, stateOf } from './appointments';

/**
 * *Mis turnos* (D-80, D-85): the patient's appointments ahead, then those of each member they act for, each with
 * "Cambiar horario" and "Cancelar" while the server says it may still be (D-111), and those of the last year. Never
 * reception's note nor who cancelled one: the API has no place for them.
 */
export function AppointmentsPage() {
  const said = (useLocation().state as { notice?: string } | null)?.notice ?? null;
  const own = useQuery({ queryKey: ['appointments'], queryFn: () => api.get<OwnAppointments>('/api/portal/appointments') });
  const cancel = useAction((id: string) => api.post(`/api/portal/appointments/${id}/cancel`), {
    refusals: CANCEL_REFUSALS, done: 'Turno cancelado.', invalidates: ['appointments'],
  });

  return (
    <Stack gap={16}>
      <Group justify="space-between">
        <Title order={1} fz={26}>Mis turnos</Title>
        <Button component={Link} to="/booking" size="md">Sacar turno</Button>
      </Group>
      <Said error={cancel.error} notice={cancel.notice ?? (cancel.error ? null : said)} />
      {own.isError && <Said error={describeError(own.error)} />}
      {own.isPending && <Loader size="sm" aria-label="Buscando tus turnos" />}
      {own.data && (
        <>
          {own.data.upcoming.length === 0
            ? <Text c="dimmed">No tenés turnos próximos.</Text>
            : own.data.upcoming.map((entry, index) => (
              <Card key={entry.id} entry={entry} label={index === 0 ? 'Próximo' : null} own
                cancelling={cancel.pending} onCancel={cancel.run} />
            ))}
          {own.data.family.map((member) => (
            <Stack key={member.patientId} gap={12} aria-label={`Turnos de ${member.fullName}`} component="section">
              {member.upcoming.length === 0 && member.past.length === 0
                && <Text c="dimmed">{member.fullName} no tiene turnos próximos ni del último año.</Text>}
              {member.upcoming.map((entry) => (
                <Card key={entry.id} entry={entry} label={member.fullName} own={false} cancelling={cancel.pending}
                  onCancel={cancel.run} />
              ))}
              {member.past.length > 0 && <Past title={`${member.fullName}, del último año`} entries={member.past} own={false} />}
            </Stack>
          ))}
          <Past title="Del último año" entries={own.data.past} own empty="No tuviste turnos en el último año." />
        </>
      )}
      <Text c="dimmed" fz={14}>Podés cambiar o cancelar hasta un día antes. Después, o para cambiar de profesional o de
        motivo, llamá o acercate a recepción.</Text>
    </Stack>
  );
}

function Card({ entry, label, own, cancelling, onCancel }: {
  entry: Entry;
  label: string | null;
  own: boolean;
  cancelling: boolean;
  onCancel: (id: string) => void;
}) {
  return (
    <article className="appointment-card" aria-label={dayAndTime(entry.startsAt)}>
      {label && <span className={own ? 'appointment-label next' : 'appointment-label'}>{label}</span>}
      <span className="appointment-when">{dayAndTime(entry.startsAt)}</span>
      <Text fz={15}>{entry.practitioner.fullName} · {entry.reason}</Text>
      {entry.state !== 'BOOKED' && <Text fz={14} c="dimmed">{stateOf(entry, own)}</Text>}
      {(entry.movable || entry.cancellable) && (
        <Group gap={8} grow>
          {entry.movable && (
            <Button component={Link} to={`/appointments/${entry.id}/move`} variant="default" size="md">
              Cambiar horario
            </Button>
          )}
          {entry.cancellable && (
            <Button variant="default" color="red" size="md" loading={cancelling} onClick={() => onCancel(entry.id)}>
              Cancelar
            </Button>
          )}
        </Group>
      )}
    </article>
  );
}

function Past({ title, entries, own, empty }: { title: string; entries: Entry[]; own: boolean; empty?: string }) {
  return (
    <Stack gap={8} component="section" aria-label={title}>
      <Title order={2} fz={18}>{title}</Title>
      {entries.length === 0 && empty && <Text c="dimmed">{empty}</Text>}
      {entries.map((entry) => (
        <Text key={entry.id} fz={15}>
          {dayAndTime(entry.startsAt)} · {entry.practitioner.fullName} · {entry.reason} — <Text span c="dimmed">
            {stateOf(entry, own)}</Text>
        </Text>
      ))}
    </Stack>
  );
}
