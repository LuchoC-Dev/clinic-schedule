import {
  Alert, Anchor, Button, Checkbox, Group, List, Loader, NativeSelect, NumberInput, Paper, Stack, Text, TextInput, Title,
} from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';

import { momentAtThePractice, todayAtThePractice } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import {
  DAYS, describeAbsence, NAME_REFUSALS, type PractitionerSetup, REASON_REFUSALS, TIME_OFF_REFUSALS, type TimeOffResult,
  timeOffBody, timeOffSaid, upcoming, WEEK_REFUSALS, type WeekRow, weekRows, weekToSend,
} from './practitioners';

type Reason = PractitionerSetup['reasons'][number];

/**
 * A practitioner set up (D-28, D-46, D-82): their name; their reasons, each always as long with them, and which are
 * booked on the web; their week, replaced whole; and their time off, recorded by the appointment module, which
 * cancels what it touches and says who arrived already (D-32).
 */
export function PractitionerPage() {
  const { id } = useParams();
  const location = useLocation();
  const registered = (location.state as { notice?: string } | null)?.notice ?? null;
  const practitioner = useQuery({ queryKey: ['practitioners', id], queryFn: () => api.get<PractitionerSetup>(`/api/practitioners/${id}`) });

  if (practitioner.isPending) {
    return <Loader aria-label="Cargando el profesional" />;
  }
  if (practitioner.isError) {
    return <Alert color="red" role="alert">{describeError(practitioner.error, NAME_REFUSALS)}</Alert>;
  }
  const data = practitioner.data;
  return (
    <Stack gap={20} maw={960}>
      <Stack gap={4}>
        <Group gap={12}>
          <Anchor component={Link} to="/practitioners" fz={14}>‹ Profesionales</Anchor>
          <Anchor component={Link} to={`/agenda?practitionerId=${data.id}`} fz={14}>Ver su agenda</Anchor>
        </Group>
        <Title order={1} fz={28}>{data.fullName}</Title>
      </Stack>
      {registered && <Said notice={registered} />}
      <NameForm practitioner={data} />
      <Reasons practitioner={data} />
      <Week practitioner={data} />
      <TimeOff practitioner={data} />
    </Stack>
  );
}

function NameForm({ practitioner }: { practitioner: PractitionerSetup }) {
  const [fullName, setFullName] = useState(practitioner.fullName);
  const rename = useAction(() => api.put(`/api/practitioners/${practitioner.id}`, { fullName: fullName.trim() }), {
    refusals: NAME_REFUSALS, invalidates: ['practitioners'], done: 'Nombre guardado.',
  });
  function submit(event: FormEvent) {
    event.preventDefault();
    rename.run(undefined);
  }
  return (
    <>
      <Title order={2} fz={20}>Nombre</Title>
      <Paper className="card" p={20}>
        <form onSubmit={submit}>
          <Group align="flex-end">
            <TextInput label="Nombre completo" required maxLength={150} value={fullName}
              onChange={(event) => setFullName(event.currentTarget.value)} />
            <Button type="submit" variant="default" loading={rename.pending}>Guardar</Button>
          </Group>
          <Said error={rename.error} notice={rename.notice} />
        </form>
      </Paper>
    </>
  );
}

function Reasons({ practitioner }: { practitioner: PractitionerSetup }) {
  const [name, setName] = useState('');
  const [minutes, setMinutes] = useState<number | string>(30);
  const add = useAction(() => api.post(`/api/practitioners/${practitioner.id}/reasons`,
    { name: name.trim(), durationMinutes: Number(minutes) }), {
    refusals: REASON_REFUSALS, invalidates: ['practitioners'], done: 'Razón agregada.',
    onDone: () => { setName(''); setMinutes(30); },
  });
  function submit(event: FormEvent) {
    event.preventDefault();
    add.run(undefined);
  }
  return (
    <>
      <Title order={2} fz={20}>Razones</Title>
      <Text c="dimmed" fz={14}>Cada razón dura siempre lo mismo con este profesional. Cambiar una duración no mueve los turnos ya
        dados. Las que se reservan por la web son las únicas que los pacientes pueden sacar desde su cuenta.</Text>
      <Paper className="card" p={20}>
        <Stack gap={16}>
          {practitioner.reasons.length === 0
            && <Text c="dimmed">Todavía no tiene razones: sin ellas no se le pueden dar turnos.</Text>}
          {practitioner.reasons.map((reason) => (
            <ReasonForm key={reason.id} practitionerId={practitioner.id} reason={reason} />
          ))}
          <form onSubmit={submit}>
            <Group align="flex-end">
              <TextInput label="Nueva razón" required maxLength={60} placeholder="consulta" value={name}
                onChange={(event) => setName(event.currentTarget.value)} />
              <NumberInput label="Minutos" required min={1} max={480} w={110} value={minutes} onChange={setMinutes} />
              <Button type="submit" loading={add.pending}>Agregar</Button>
            </Group>
            <Said error={add.error} notice={add.notice} />
          </form>
        </Stack>
      </Paper>
    </>
  );
}

function ReasonForm({ practitionerId, reason }: { practitionerId: string; reason: Reason }) {
  const [name, setName] = useState(reason.name);
  const [minutes, setMinutes] = useState<number | string>(reason.durationMinutes);
  const [webBookable, setWebBookable] = useState(reason.webBookable);
  const save = useAction(() => api.put(`/api/practitioners/${practitionerId}/reasons/${reason.id}`,
    { name: name.trim(), durationMinutes: Number(minutes), webBookable }), {
    refusals: REASON_REFUSALS, invalidates: ['practitioners'], done: 'Razón guardada.',
  });
  function submit(event: FormEvent) {
    event.preventDefault();
    save.run(undefined);
  }
  return (
    <form onSubmit={submit} aria-label={`Razón ${reason.name}`}>
      <Group align="flex-end">
        <TextInput label="Razón" required maxLength={60} value={name} onChange={(event) => setName(event.currentTarget.value)} />
        <NumberInput label="Minutos" required min={1} max={480} w={110} value={minutes} onChange={setMinutes} />
        <Checkbox label="Se reserva por la web" checked={webBookable} mb={8}
          onChange={(event) => setWebBookable(event.currentTarget.checked)} />
        <Button type="submit" variant="default" loading={save.pending}>Guardar</Button>
      </Group>
      <Said error={save.error} notice={save.notice} />
    </form>
  );
}

function Week({ practitioner }: { practitioner: PractitionerSetup }) {
  const [rows, setRows] = useState<WeekRow[]>(() => weekRows(practitioner.weeklySchedule));
  const [refusal, setRefusal] = useState<string | null>(null);
  const save = useAction((hours: unknown) => api.put<PractitionerSetup>(`/api/practitioners/${practitioner.id}/weekly-schedule`,
    { hours }), {
    refusals: WEEK_REFUSALS, invalidates: ['practitioners'], done: 'Semana guardada.',
    // The week as the server keeps it, in its order, with the empty rows again.
    onDone: (saved) => setRows(weekRows(saved.weeklySchedule)),
  });
  const change = (index: number, key: keyof WeekRow) => (event: { currentTarget: { value: string } }) => {
    const { value } = event.currentTarget;
    setRows((before) => before.map((row, at) => (at === index ? { ...row, [key]: value } : row)));
  };
  function submit(event: FormEvent) {
    event.preventDefault();
    const week = weekToSend(rows);
    if ('refusal' in week) {
      save.reset();
      setRefusal(week.refusal);
      return;
    }
    setRefusal(null);
    save.run(week.hours);
  }
  return (
    <>
      <Title order={2} fz={20}>Semana</Title>
      <Text c="dimmed" fz={14}>Un tramo por fila. Una pausa es el hueco entre dos tramos del mismo día. Para sacar un tramo,
        vaciá su fila. Se guarda la semana entera.</Text>
      <Paper className="card" p={20}>
        <form onSubmit={submit}>
          <Stack gap={8}>
            {rows.map((row, index) => (
              <Group key={index} align="flex-end" aria-label={`Tramo ${index + 1}`} role="group">
                <NativeSelect label="Día" value={row.day} onChange={change(index, 'day')}
                  data={[{ value: '', label: '—' }, ...DAYS]} />
                <TextInput label="Desde" type="time" value={row.startsAt} onChange={change(index, 'startsAt')} />
                <TextInput label="Hasta" type="time" value={row.endsAt} onChange={change(index, 'endsAt')} />
              </Group>
            ))}
            <Group><Button type="submit" loading={save.pending}>Guardar semana</Button></Group>
            <Said error={refusal ?? save.error} notice={save.notice} />
          </Stack>
        </form>
      </Paper>
    </>
  );
}

function TimeOff({ practitioner }: { practitioner: PractitionerSetup }) {
  const today = todayAtThePractice(new Date());
  const [form, setForm] = useState({ date: today, untilDate: '', startsAt: '', endsAt: '' });
  const [warning, setWarning] = useState<string | null>(null);
  const record = useAction(() => api.post<TimeOffResult>('/api/time-off', timeOffBody(practitioner.id, form)), {
    refusals: TIME_OFF_REFUSALS, invalidates: ['practitioners', 'agenda'],
    done: (result) => timeOffSaid(result).notice,
    onDone: (result) => setWarning(timeOffSaid(result).warning),
  });
  const set = (key: keyof typeof form) => (event: { currentTarget: { value: string } }) => {
    const { value } = event.currentTarget;
    setForm((before) => ({ ...before, [key]: value }));
  };
  const coming = upcoming(practitioner.timeOff, momentAtThePractice(new Date()));
  function submit(event: FormEvent) {
    event.preventDefault();
    setWarning(null);
    record.run(undefined);
  }
  return (
    <>
      <Title order={2} fz={20}>Ausencias</Title>
      <Paper className="card" p={20}>
        <Stack gap={12}>
          {coming.length === 0 && <Text c="dimmed">No tiene ausencias próximas.</Text>}
          {coming.length > 0 && (
            <List aria-label="Ausencias próximas">
              {coming.map((absence) => <List.Item key={absence.startsAt}>{describeAbsence(absence)}</List.Item>)}
            </List>
          )}
          <form onSubmit={submit}>
            <Group align="flex-end">
              <TextInput label="Desde el día" type="date" required min={today} value={form.date} onChange={set('date')} />
              <TextInput label="Hasta el día (opcional)" type="date" min={today} value={form.untilDate} onChange={set('untilDate')} />
              <TextInput label="Desde la hora (opcional)" type="time" value={form.startsAt} onChange={set('startsAt')} />
              <TextInput label="Hasta la hora" type="time" value={form.endsAt} onChange={set('endsAt')} />
              <Button type="submit" loading={record.pending}>Registrar ausencia</Button>
            </Group>
            <Stack gap={8} mt={12}>
              <Said error={record.error} notice={record.notice} />
              {warning && <Alert color="amber" role="alert">{warning}</Alert>}
            </Stack>
          </form>
          <Text c="dimmed" fz={14}>Sin horario son días completos. Con horario es una franja de un solo día. Cancela los turnos
            que toca, salvo los de pacientes que ya llegaron.</Text>
        </Stack>
      </Paper>
    </>
  );
}
