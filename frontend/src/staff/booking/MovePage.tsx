import { Alert, Button, Chip, Group, Loader, Paper, Stack, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';

import { longDay, timeOf } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { type Agenda, patientName } from '../agenda/agenda';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import { AGENDA_REFUSALS } from '../words';
import { type FreeTimes, hourOf, noTimesSaid, startOf } from './booking';

/**
 * Moving an appointment, keeping its length (D-32): to one of the free times of the day chosen, its own not taken,
 * or to another typed by hand. The appointment is found on its day's agenda, which the address names, so a reload
 * keeps the screen.
 */
export function MovePage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const from = params.get('date') ?? '';
  const [date, setDate] = useState(from);
  const [time, setTime] = useState<string | null>(null);
  const [otherTime, setOtherTime] = useState('');
  const [why, setWhy] = useState<string | null>(null);

  const agenda = useQuery({ queryKey: ['agenda', from, null, false],
    queryFn: () => api.get<Agenda>('/api/agenda', { date: from }) });
  const appointment = agenda.data?.appointments.find((each) => each.id === id);
  const freeTimes = useQuery({
    queryKey: ['free-times', id, date],
    queryFn: () => api.get<FreeTimes>('/api/agenda/free-times', { appointmentId: id, date }),
    enabled: Boolean(appointment && date),
  });
  const start = date ? startOf(date, time, otherTime) : null;
  const back = () => navigate(`/agenda?date=${from}&practitionerId=${appointment?.practitionerId ?? ''}`);
  const move = useAction(() => api.post(`/api/appointments/${id}/reschedule`, { startsAt: start }), {
    refusals: AGENDA_REFUSALS,
    invalidates: ['agenda', 'free-times', 'patient-appointments'],
    onDone: () => navigate(`/agenda?date=${date}&practitionerId=${appointment?.practitionerId ?? ''}`,
      { state: { notice: 'Turno movido.' } }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setWhy(start ? null : 'Elegí un horario.');
    if (start) {
      move.run(undefined);
    }
  }

  if (agenda.isPending) {
    return <Loader aria-label="Cargando el turno" />;
  }
  if (agenda.isError || !appointment) {
    return (
      <Alert color="red" role="alert">
        {agenda.isError ? describeError(agenda.error) : 'No se encontró el turno en la agenda de ese día.'}
      </Alert>
    );
  }
  return (
    <form onSubmit={submit}>
      <Stack gap={20} maw={720}>
        <Title order={1} fz={28}>Mover turno</Title>
        <Paper className="card" p={20}>
          <Text fw={600}>{patientName(appointment)} — {appointment.reason}</Text>
          <Text c="dimmed" fz={14}>
            Ahora: {longDay(from)} de {timeOf(appointment.startsAt)} a {timeOf(appointment.endsAt)}, con{' '}
            {appointment.practitioner}
          </Text>
        </Paper>
        <Paper className="card" p={20}>
          <Stack>
            <TextInput label="Nuevo día" type="date" w={200} value={date}
              onChange={(event) => { setDate(event.currentTarget.value); setTime(null); }} />
            {freeTimes.data && (
              <>
                <Title order={2} fz={18}>Horarios libres el {longDay(date)}</Title>
                {noTimesSaid(freeTimes.data, appointment.practitioner ?? 'El profesional') && (
                  <Text c="dimmed">{noTimesSaid(freeTimes.data, appointment.practitioner ?? 'El profesional')}</Text>
                )}
                <Chip.Group value={time} onChange={(value) => setTime(value as string)}>
                  <Group gap={6} role="group" aria-label="Horarios libres">
                    {freeTimes.data.times.map((each) => (
                      <Chip key={each} value={each} variant="outline" className="mono">{hourOf(each)}</Chip>
                    ))}
                  </Group>
                </Chip.Group>
              </>
            )}
            <TextInput label="…u otra hora" type="time" w={160} value={otherTime}
              onChange={(event) => setOtherTime(event.currentTarget.value)} />
          </Stack>
        </Paper>
        <Said error={why ?? move.error} />
        <Group>
          <Button type="submit" loading={move.pending}>Mover</Button>
          <Button variant="default" onClick={back}>Volver</Button>
        </Group>
      </Stack>
    </form>
  );
}
