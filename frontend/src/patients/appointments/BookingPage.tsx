import { Anchor, Button, Loader, NativeSelect, Stack, Text, Title } from '@mantine/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';

import { describeError } from '../../shared/errors';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { dayAndTime } from '../words';
import {
  type BookingOptions, bookingRefusals, daysBetween, type Done, doneSaid, type Times, untilSaid,
} from './appointments';
import { TimePicker } from './TimePicker';

/**
 * *Sacar turno* (D-82, D-85): for whom, when there is someone else to book for; a reason booked on the web, with its
 * practitioner; a day from tomorrow on; and one of the times the agenda offers, chosen and then confirmed. The choice
 * is in the address, as on the Thymeleaf screen, so going back keeps it.
 */
export function BookingPage() {
  const navigate = useNavigate();
  const queries = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [time, setTime] = useState<string | null>(null);
  const options = useQuery({ queryKey: ['booking-options'],
    queryFn: () => api.get<BookingOptions>('/api/portal/booking/options') });
  const reasons = (options.data?.practitioners ?? []).flatMap((practitioner) => practitioner.reasons.map((reason) => ({
    value: reason.id, label: `${practitioner.fullName} — ${reason.name}`, practitioner,
  })));
  const reason = reasons.find((each) => each.value === params.get('reason')) ?? reasons[0];
  const whom = options.data?.patients ?? [];
  const patient = whom.find((each) => each.id === params.get('patient')) ?? whom[0];
  const member = patient && patient !== whom[0] ? patient.fullName : null;
  const day = params.get('date') ?? options.data?.firstDay ?? '';
  const times = useQuery({
    queryKey: ['booking-times', reason?.practitioner.id, reason?.value, day],
    enabled: Boolean(reason && day),
    queryFn: () => api.get<Times>('/api/portal/booking/times',
      { practitionerId: reason!.practitioner.id, reasonId: reason!.value, date: day }),
  });
  const book = useAction(() => api.post<Done>('/api/portal/appointments', {
    patientId: member ? patient.id : null, practitionerId: reason!.practitioner.id, reasonId: reason!.value,
    startsAt: `${day}T${time}:00`,
  }), {
    refusals: bookingRefusals(member),
    invalidates: ['appointments'],
    onDone: (done) => navigate('/appointments', { state: { notice: doneSaid('reservado', done, member) } }),
  });

  function choose(changes: Record<string, string>) {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([name, value]) => next.set(name, value));
    setParams(next, { replace: true });
    setTime(null);
    book.reset();
  }

  if (options.isError) {
    return <Said error={describeError(options.error)} />;
  }
  if (!options.data) {
    return <Loader size="sm" aria-label="Buscando qué se puede reservar" />;
  }
  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>Sacar turno</Title>
      {reasons.length === 0 ? (
        <Text c="dimmed">Por ahora no hay turnos para sacar por la web: llamá o acercate a recepción.</Text>
      ) : (
        <>
          {whom.length > 1 && (
            <NativeSelect label="Para" size="md" value={patient.id} onChange={(event) => choose({ patient: event.currentTarget.value })}
              data={whom.map((each, index) => ({ value: each.id, label: index === 0 ? `${each.fullName} (vos)` : each.fullName }))} />
          )}
          <NativeSelect label="Profesional y motivo" size="md" value={reason.value}
            onChange={(event) => choose({ reason: event.currentTarget.value })}
            data={reasons.map(({ value, label }) => ({ value, label }))} />
          <TimePicker days={daysBetween(options.data.firstDay, options.data.lastDay)} day={day}
            onDay={(each) => choose({ date: each })} times={times.data?.times} loading={times.isPending}
            error={times.isError ? describeError(times.error, bookingRefusals(member)) : null}
            time={time} onTime={setTime} />
          <Text c="dimmed" fz={14}>{untilSaid(options.data.lastDay)}. Para hoy, llamá o acercate a recepción.</Text>
          <Said error={book.error} />
          <Button size="md" disabled={!time} loading={book.pending} onClick={() => {
            book.run(undefined);
            void queries.invalidateQueries({ queryKey: ['booking-times'] });
          }}>
            {time ? `Reservar: ${dayAndTime(`${day}T${time}`)}` : 'Elegí un horario'}
          </Button>
        </>
      )}
      <Anchor component={Link} to="/appointments">Volver a mis turnos</Anchor>
    </Stack>
  );
}
