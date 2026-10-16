import { Anchor, Button, Loader, Paper, Stack, Text, Title } from '@mantine/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';

import { describeError } from '../../shared/errors';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { dayAndTime } from '../words';
import { daysBetween, type Done, doneSaid, MOVE_REFUSALS, type MoveTarget, type Times, untilSaid } from './appointments';
import { TimePicker } from './TimePicker';

/**
 * *Cambiar horario* (D-93), as the approved mock has it: the appointment as it stands, a day and one of the times
 * the agenda offers to move it to, confirmed; the same practitioner and reason. Done, it says the new time, and that
 * the email's old link no longer works. The day is in the address, so going back keeps it.
 */
export function MovePage() {
  const { id } = useParams();
  const queries = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [time, setTime] = useState<string | null>(null);
  const [moved, setMoved] = useState<string | null>(null);
  const target = useQuery({ queryKey: ['appointments', id],
    queryFn: () => api.get<MoveTarget>(`/api/portal/appointments/${id}`) });
  const day = params.get('date') ?? target.data?.firstDay ?? '';
  const times = useQuery({
    queryKey: ['move-times', id, day],
    enabled: Boolean(target.data?.movable && day),
    queryFn: () => api.get<Times>(`/api/portal/appointments/${id}/move-times`, { date: day }),
  });
  const move = useAction(() => api.post<Done>(`/api/portal/appointments/${id}/move`, { startsAt: `${day}T${time}:00` }), {
    refusals: MOVE_REFUSALS,
    invalidates: ['appointments', 'move-times'],
    onDone: (done) => setMoved(doneSaid('cambiado', done, target.data?.member ?? null)),
  });

  function chooseDay(each: string) {
    setParams({ date: each }, { replace: true });
    setTime(null);
    move.reset();
  }

  if (moved) {
    return (
      <Stack gap={16} align="center" ta="center" py={16}>
        <Title order={1} fz={24}>Turno cambiado</Title>
        <Text fz={16}>{moved} El link del email anterior ya no sirve.</Text>
        <Anchor component={Link} to="/appointments" fw={600}>Volver a mis turnos</Anchor>
      </Stack>
    );
  }
  if (target.isError) {
    return <Said error={describeError(target.error)} />;
  }
  if (!target.data) {
    return <Loader size="sm" aria-label="Buscando el turno" />;
  }
  const appointment = target.data;
  return (
    <Stack gap={18}>
      <Title order={1} fz={26}>Cambiar horario</Title>
      <Paper className="card" p={14}>
        <Text fz={13} c="dimmed">{appointment.member ? `Turno de ${appointment.member}, ahora` : 'Ahora'}</Text>
        <Text fw={600}>{dayAndTime(appointment.startsAt)} · {appointment.practitioner.fullName} · {appointment.reason}</Text>
      </Paper>
      {appointment.movable ? (
        <>
          <TimePicker days={daysBetween(appointment.firstDay, appointment.lastDay)} day={day} onDay={chooseDay}
            times={times.data?.times} loading={times.isPending}
            error={times.isError ? describeError(times.error, MOVE_REFUSALS) : null} time={time} onTime={setTime} />
          <Text c="dimmed" fz={14}>{untilSaid(appointment.lastDay)}, con el mismo profesional y el mismo motivo. Para
            cambiar de profesional o de motivo, cancelalo y sacá otro.</Text>
          <Said error={move.error} />
          <Button size="md" disabled={!time} loading={move.pending} onClick={() => {
            move.run(undefined);
            void queries.invalidateQueries({ queryKey: ['move-times'] });
          }}>
            {time ? `Cambiar al ${dayAndTime(`${day}T${time}`)}` : 'Elegí un horario'}
          </Button>
        </>
      ) : (
        <Text c="dimmed">Este turno ya no se puede cambiar por la web: falta menos de un día, o ya no está reservado.
          Llamá o acercate a recepción.</Text>
      )}
      <Anchor component={Link} to="/appointments">Volver a mis turnos</Anchor>
    </Stack>
  );
}
