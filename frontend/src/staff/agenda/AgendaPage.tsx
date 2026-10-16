import { Alert, Anchor, Button, Chip, Group, Loader, Menu, SegmentedControl, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';

import { addDays, longDay, timeOf } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { AGENDA_REFUSALS } from '../words';
import { type Agenda, type AgendaAppointment, type Tone, actionsFor, daysOf, patientName, stateOf, summaryOf } from './agenda';
import './agenda.css';
import { ArrivalDialog, CancelDialog } from './dialogs';

/**
 * Reception's agenda (D-98, D-100): a day or its week, of everyone or of one practitioner, with the day's
 * operations — the arrival, charged as it comes in (D-65, D-87), a warning of lateness, moving, cancelling — and
 * booking. The day, the view and the practitioner are in the address, so going back and reloading keep them;
 * what is shown — the days, the practitioner chosen — is what the server answered, so the choice never disagrees
 * with the agenda (D-45).
 */
export function AgendaPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const date = params.get('date');
  const practitionerId = params.get('practitionerId');
  const week = params.get('view') === 'week';
  const agenda = useQuery({
    queryKey: ['agenda', date, practitionerId, week],
    queryFn: () => api.get<Agenda>('/api/agenda', { date, practitionerId, week: week ? 'true' : null }),
  });
  const [arriving, setArriving] = useState<AgendaAppointment | null>(null);
  const [cancelling, setCancelling] = useState<AgendaAppointment | null>(null);
  const warnLate = useAction(
    (appointment: AgendaAppointment) => api.post(`/api/appointments/${appointment.id}/late-arrival-notice`),
    { refusals: AGENDA_REFUSALS, done: 'Anotado: se lo espera hasta el final del día.', invalidates: ['agenda'] },
  );
  const booked = (location.state as { notice?: string } | null)?.notice ?? null;
  const [arrived, setArrived] = useState<string | null>(null);

  function show(next: { date?: string | null; practitionerId?: string | null; week?: boolean }) {
    const wanted = { date, practitionerId, week, ...next };
    const search = new URLSearchParams();
    if (wanted.date) search.set('date', wanted.date);
    if (wanted.practitionerId) search.set('practitionerId', wanted.practitionerId);
    if (wanted.week) search.set('view', 'week');
    setParams(search);
  }

  const shown = agenda.data;
  const step = week ? 7 : 1;
  const days = shown ? daysOf(shown, addDays) : [];
  return (
    <Stack gap={20}>
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <Stack gap={4}>
          <Title order={1} fz={28}>{week ? 'Agenda de la semana' : 'Agenda del día'}</Title>
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
          <Button onClick={() => navigate(bookingAddress(shown, practitionerId))}>Nuevo turno</Button>
        </Group>
      </Group>

      {agenda.isError && <Alert color="red" role="alert">{describeError(agenda.error)}</Alert>}
      {agenda.isPending && <Loader aria-label="Cargando la agenda" />}
      <Said error={warnLate.error} notice={warnLate.notice ?? arrived ?? booked} />

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

          {days.map(({ day, appointments }) => (
            <Stack key={day} gap={8}>
              {week && <Title order={2} fz={18} className="agenda-day">{longDay(day)}</Title>}
              <section className="card agenda-table">
                {appointments.length === 0
                  ? <Text p={20} c="dimmed">No hay turnos reservados para este día.</Text>
                  : <AgendaTable appointments={appointments} onArrive={setArriving} onCancel={setCancelling}
                      onWarnLate={(appointment) => warnLate.run(appointment)}
                      onMove={(appointment) => navigate(
                        `/agenda/appointments/${appointment.id}/move?date=${appointment.startsAt.slice(0, 10)}`)} />}
              </section>
            </Stack>
          ))}
        </>
      )}
      {arriving && <ArrivalDialog appointment={arriving} onClose={() => setArriving(null)}
        onDone={() => setArrived('Llegó: está en la cola de su profesional.')} />}
      {cancelling && <CancelDialog appointment={cancelling} onClose={() => setCancelling(null)} />}
    </Stack>
  );
}

function AgendaTable({ appointments, onArrive, onCancel, onWarnLate, onMove }: {
  appointments: AgendaAppointment[];
  onArrive: (appointment: AgendaAppointment) => void;
  onCancel: (appointment: AgendaAppointment) => void;
  onWarnLate: (appointment: AgendaAppointment) => void;
  onMove: (appointment: AgendaAppointment) => void;
}) {
  return (
    <Table className="card-table" miw={760} verticalSpacing={12} horizontalSpacing={14}>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Hora</Table.Th>
          <Table.Th>Paciente</Table.Th>
          <Table.Th>Profesional · motivo</Table.Th>
          <Table.Th>Estado</Table.Th>
          <Table.Th style={{ textAlign: 'right' }}>Acciones</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {appointments.map((appointment) => {
          const state = stateOf(appointment);
          const can = actionsFor(appointment);
          return (
            <Table.Tr key={appointment.id} className={state.tone === 'absent' ? 'agenda-past' : undefined}>
              <Table.Td className="mono" style={{ whiteSpace: 'nowrap' }}>
                {timeOf(appointment.startsAt)}
                <span className="agenda-minutes"> · {appointment.durationMinutes}′</span>
              </Table.Td>
              <Table.Td>
                <Stack gap={4}>
                  <Anchor component={Link} to={`/patients/${appointment.patientId}/appointments`} fw={600} c="inherit"
                    underline="never">
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
              <Table.Td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                {can.arrive && <Button variant="default" onClick={() => onArrive(appointment)}>Llegó</Button>}
                {(can.warnLate || can.move || can.cancel) && (
                  <Menu position="bottom-end">
                    <Menu.Target>
                      <Button variant="default" ml={6} aria-label={`Más acciones de ${patientName(appointment)}`}>⋯</Button>
                    </Menu.Target>
                    <Menu.Dropdown>
                      {can.warnLate && <Menu.Item onClick={() => onWarnLate(appointment)}>Avisó demora</Menu.Item>}
                      {can.move && <Menu.Item onClick={() => onMove(appointment)}>Mover</Menu.Item>}
                      {can.cancel && <Menu.Item color="red" onClick={() => onCancel(appointment)}>Cancelar</Menu.Item>}
                    </Menu.Dropdown>
                  </Menu>
                )}
              </Table.Td>
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

/** Booking from a practitioner's agenda comes with them chosen, and the day shown (D-45). */
function bookingAddress(shown: Agenda | undefined, practitionerId: string | null) {
  const search = new URLSearchParams();
  if (shown) search.set('date', shown.date);
  const whose = shown?.practitionerId ?? practitionerId;
  if (whose) search.set('practitionerId', whose);
  const query = search.toString();
  return query ? `/agenda/new?${query}` : '/agenda/new';
}
