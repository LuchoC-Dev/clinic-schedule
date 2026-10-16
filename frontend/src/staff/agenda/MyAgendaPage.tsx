import { Alert, Anchor, Button, Group, Loader, SegmentedControl, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';

import { addDays, longDay, timeOf, todayAtThePractice } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { api } from '../session';
import { type Agenda, type AgendaAppointment, daysOf, patientName, stateOf, summaryOf } from './agenda';
import './agenda.css';

/**
 * A practitioner's own agenda (D-39, D-104): a day or its week, read only, as the Thymeleaf agenda showed it to them.
 * Today's patients lead to their record, which they may read even before the patient arrives (D-49); those who
 * arrived are in their queue, where the consultation starts (D-65). The day and the view are in the address.
 */
export function MyAgendaPage() {
  const [params, setParams] = useSearchParams();
  const date = params.get('date');
  const week = params.get('view') === 'week';
  const agenda = useQuery({
    queryKey: ['agenda', date, null, week],
    queryFn: () => api.get<Agenda>('/api/agenda', { date, week: week ? 'true' : null }),
  });

  function show(next: { date?: string | null; week?: boolean }) {
    const wanted = { date, week, ...next };
    const search = new URLSearchParams();
    if (wanted.date) search.set('date', wanted.date);
    if (wanted.week) search.set('view', 'week');
    setParams(search);
  }

  const shown = agenda.data;
  const step = week ? 7 : 1;
  const whose = shown?.practitioners.find((each) => each.id === shown.practitionerId)?.fullName;
  const today = todayAtThePractice(new Date());
  return (
    <Stack gap={20}>
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <Stack gap={4}>
          <Title order={1} fz={28}>{whose ? `Agenda de ${whose}` : 'Mi agenda'}</Title>
          {shown && (
            <Text c="dimmed" fz={15}>
              <span className="agenda-day">
                {week ? `${longDay(shown.from)} al ${longDay(shown.to)}` : longDay(shown.date)}
              </span> · {summaryOf(shown.appointments)}
            </Text>
          )}
        </Stack>
        <Group gap={8}>
          <SegmentedControl value={week ? 'week' : 'day'} onChange={(view) => show({ week: view === 'week' })}
            data={[{ value: 'day', label: 'Día' }, { value: 'week', label: 'Semana' }]} />
          <Button variant="default" aria-label={week ? 'Semana anterior' : 'Día anterior'} disabled={!shown}
            onClick={() => shown && show({ date: addDays(shown.date, -step) })}>‹</Button>
          <Button variant="default" onClick={() => show({ date: null })}>Hoy</Button>
          <Button variant="default" aria-label={week ? 'Semana siguiente' : 'Día siguiente'} disabled={!shown}
            onClick={() => shown && show({ date: addDays(shown.date, step) })}>›</Button>
        </Group>
      </Group>

      {agenda.isError && <Alert color="red" role="alert">{describeError(agenda.error)}</Alert>}
      {agenda.isPending && <Loader aria-label="Cargando la agenda" />}

      {shown && daysOf(shown, addDays).map(({ day, appointments }) => (
        <Stack key={day} gap={8}>
          {week && <Title order={2} fz={18} className="agenda-day">{longDay(day)}</Title>}
          <section className="card agenda-table">
            {appointments.length === 0
              ? <Text p={20} c="dimmed">No hay turnos reservados para este día.</Text>
              : <Rows appointments={appointments} today={today} />}
          </section>
        </Stack>
      ))}
    </Stack>
  );
}

function Rows({ appointments, today }: { appointments: AgendaAppointment[]; today: string }) {
  return (
    <Table className="card-table" miw={620} verticalSpacing={12} horizontalSpacing={14}>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Hora</Table.Th><Table.Th>Paciente</Table.Th><Table.Th>Motivo</Table.Th><Table.Th>Estado</Table.Th>
          <Table.Th />
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {appointments.map((appointment) => {
          const state = stateOf(appointment);
          const isToday = appointment.startsAt.slice(0, 10) === today;
          return (
            <Table.Tr key={appointment.id} className={state.tone === 'absent' ? 'agenda-past' : undefined}>
              <Table.Td className="mono" style={{ whiteSpace: 'nowrap' }}>
                {timeOf(appointment.startsAt)}<span className="agenda-minutes"> · {appointment.durationMinutes}′</span>
              </Table.Td>
              <Table.Td>
                <Stack gap={4}>
                  {isToday
                    ? <Anchor component={Link} to={`/clinical-records/${appointment.patientId}`} fw={600} c="inherit">
                        {patientName(appointment)}
                      </Anchor>
                    : <Text fw={600}>{patientName(appointment)}</Text>}
                  {appointment.note && <Text fz={13} c="dimmed">{appointment.note}</Text>}
                </Stack>
              </Table.Td>
              <Table.Td c="#3A4442">
                {appointment.reason}
                {appointment.bookedVia === 'WEB' && <Text fz={13} c="amber.7">Reservado por la web</Text>}
                {appointment.movedOnWeb && <Text fz={13} c="amber.7">↻ Movido por la web</Text>}
              </Table.Td>
              <Table.Td><span className={`agenda-badge agenda-badge-${state.tone}`}>{state.label}</span></Table.Td>
              <Table.Td style={{ textAlign: 'right' }}>
                {isToday && appointment.arrivedAt
                  && <Anchor component={Link} to="/my-queue" fz={14}>En tu cola</Anchor>}
              </Table.Td>
            </Table.Tr>
          );
        })}
      </Table.Tbody>
    </Table>
  );
}
