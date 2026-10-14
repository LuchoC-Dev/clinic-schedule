import { Alert, Anchor, Button, Chip, Group, Loader, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { addDays, longDay, timeOf } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { api } from '../session';
import { type Agenda, type AgendaAppointment, type Tone, patientName, stateOf, summaryOf } from './agenda';
import './agenda.css';

/**
 * Reception's agenda of a day (D-98), read only for now: the day and whose agenda it is are in the address, so
 * going back and reloading keep them; what is shown — the day, and the practitioner chosen — is what the server
 * answered, so the choice never disagrees with the agenda (D-45).
 *
 * Arriving, moving, cancelling and booking stay on the Thymeleaf agenda until reception's screens move here,
 * reached from this one on the same day.
 */
export function AgendaPage() {
  const [params, setParams] = useSearchParams();
  const date = params.get('date');
  const practitionerId = params.get('practitionerId');
  const agenda = useQuery({
    queryKey: ['agenda', date, practitionerId],
    queryFn: () => api.get<Agenda>('/api/agenda', { date, practitionerId }),
  });

  function show(next: { date?: string | null; practitionerId?: string | null }) {
    const wanted = { date, practitionerId, ...next };
    const search = new URLSearchParams();
    if (wanted.date) search.set('date', wanted.date);
    if (wanted.practitionerId) search.set('practitionerId', wanted.practitionerId);
    setParams(search);
  }

  const shown = agenda.data;
  const thymeleaf = shown ? screenAddress('/agenda', shown) : '/agenda';
  return (
    <Stack gap={20}>
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <Stack gap={4}>
          <Title order={1} fz={28}>Agenda del día</Title>
          {shown && (
            <Text c="dimmed" fz={15}>
              <span className="agenda-day">{longDay(shown.date)}</span> · {summaryOf(shown.appointments)}
            </Text>
          )}
        </Stack>
        <Group gap={8}>
          <Button variant="default" aria-label="Día anterior" disabled={!shown}
            onClick={() => shown && show({ date: addDays(shown.date, -1) })}>‹</Button>
          <Button variant="default" onClick={() => show({ date: null })}>Hoy</Button>
          <Button variant="default" aria-label="Día siguiente" disabled={!shown}
            onClick={() => shown && show({ date: addDays(shown.date, 1) })}>›</Button>
          <Button component="a" href={shown ? screenAddress('/agenda/new', shown) : '/agenda/new'}>Nuevo turno</Button>
        </Group>
      </Group>

      {agenda.isError && <Alert color="red" role="alert">{describeError(agenda.error)}</Alert>}
      {agenda.isPending && <Loader aria-label="Cargando la agenda" />}

      {shown && (
        <>
          <Chip.Group value={shown.practitionerId ?? ''}
            onChange={(value) => show({ practitionerId: (value as string) || null })}>
            <Group gap={8} role="group" aria-label="Profesional">
              <Chip value="" variant="outline">Todos</Chip>
              {shown.practitioners.map((each) => (
                <Chip key={each.id} value={each.id} variant="outline">{each.fullName}</Chip>
              ))}
            </Group>
          </Chip.Group>

          <section className="card agenda-table">
            {shown.appointments.length === 0
              ? <Text p={20} c="dimmed">No hay turnos reservados para este día.</Text>
              : <AgendaTable appointments={shown.appointments} />}
          </section>
          <Text fz={14} c="dimmed">
            Para dar la llegada, avisar una demora, mover o cancelar un turno, usá
            {' '}<Anchor href={thymeleaf}>la agenda de siempre</Anchor> en este mismo día.
          </Text>
        </>
      )}
    </Stack>
  );
}

function AgendaTable({ appointments }: { appointments: AgendaAppointment[] }) {
  return (
    <Table className="card-table" miw={720} verticalSpacing={12} horizontalSpacing={14}>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Hora</Table.Th>
          <Table.Th>Paciente</Table.Th>
          <Table.Th>Profesional · motivo</Table.Th>
          <Table.Th>Estado</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {appointments.map((appointment) => {
          const state = stateOf(appointment);
          return (
            <Table.Tr key={appointment.id} className={state.tone === 'absent' ? 'agenda-past' : undefined}>
              <Table.Td className="mono" style={{ whiteSpace: 'nowrap' }}>
                {timeOf(appointment.startsAt)}
                <span className="agenda-minutes"> · {appointment.durationMinutes}′</span>
              </Table.Td>
              <Table.Td>
                <Stack gap={4}>
                  <Anchor href={`/agenda/patients/${appointment.patientId}`} fw={600} c="inherit" underline="never">
                    {patientName(appointment)}
                  </Anchor>
                  {appointment.patientDocument && <Text fz={13} c="dimmed">{appointment.patientDocument}</Text>}
                  {appointment.bookedVia === 'WEB' && <Text fz={13} c="amber.7">Reservado por la web</Text>}
                  {appointment.movedOnWeb && <Text fz={13} c="amber.7">↻ Movido por la web</Text>}
                  {appointment.note && <Text fz={13} c="dimmed">{appointment.note}</Text>}
                </Stack>
              </Table.Td>
              <Table.Td c="#3A4442">
                {appointment.practitioner ?? '(profesional desconocido)'}
                <Text fz={13} c="dimmed">{appointment.reason}</Text>
              </Table.Td>
              <Table.Td><span className={badgeClass(state.tone)}>{state.label}</span></Table.Td>
            </Table.Tr>
          );
        })}
      </Table.Tbody>
    </Table>
  );
}

function badgeClass(tone: Tone) {
  return `agenda-badge agenda-badge-${tone}`;
}

/** The same day and practitioner on a Thymeleaf screen. */
function screenAddress(path: string, agenda: Agenda) {
  const search = new URLSearchParams({ date: agenda.date });
  if (agenda.practitionerId) search.set('practitionerId', agenda.practitionerId);
  return `${path}?${search}`;
}
