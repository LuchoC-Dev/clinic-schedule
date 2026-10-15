import { Alert, Button, Group, Loader, NativeSelect, Paper, SimpleGrid, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router';

import { timeOf, when } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { pesos } from '../../shared/money';
import { tiles } from '../cash/cash';
import { Totals } from '../cash/Totals';
import { useAddressSearch } from '../components/useAddressSearch';
import { api } from '../session';
import type { Practitioner } from '../types';
import { CANCELLATION_ORIGINS, MEANS, REQUESTER } from '../words';
import {
  type CancellationLog, cancelledByForAdmins, type CashLog, LOG_REFUSALS, loggedPatient, loggedPractitioner, READ_ACCESS,
  readingWhat, type Readings, refusalWhat, type Refusals,
} from './admin';

/** Who read which clinical record (D-71): who, whose, when and by which access — never what was read. */
export function ReadingsPage() {
  const search = useAddressSearch(['patient', 'practitionerId', 'from', 'to'] as const);
  const practitioners = usePractitioners();
  const found = useQuery({ queryKey: ['record-readings', search.query],
    queryFn: () => api.get<Readings>('/api/record-readings', search.query) });
  return (
    <Log title="Lecturas de historias clínicas" about="Quién leyó la historia de qué paciente, cuándo y con qué acceso. Nunca lo que leyó."
      error={found.error} limitReached={found.data?.limitReached} more="Hay más lecturas de las que se muestran"
      empty={found.data?.readings.length === 0 ? 'No hay lecturas con esos datos.' : null}
      form={(
        <form onSubmit={search.submit}>
          <Group align="flex-end">
            <TextInput label="Paciente (nombre, documento o alias)" value={search.typed.patient} onChange={search.type('patient')} />
            <NativeSelect label="Profesional" value={search.typed.practitionerId} onChange={search.type('practitionerId')}
              data={[{ value: '', label: '— todos —' }, ...practitioners.map((each) => ({ value: each.id, label: each.fullName }))]} />
            <Days search={search} answered={found.data} />
          </Group>
        </form>
      )}>
      {found.data && found.data.readings.length > 0 && (
        <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
          <Table.Thead><Table.Tr><Table.Th>Cuándo</Table.Th><Table.Th>Profesional</Table.Th><Table.Th>Usuario</Table.Th>
            <Table.Th>Paciente</Table.Th><Table.Th>Acceso</Table.Th><Table.Th>Qué</Table.Th></Table.Tr></Table.Thead>
          <Table.Tbody>
            {found.data.readings.map((reading, index) => (
              <Table.Tr key={index}>
                <Table.Td className="mono">{when(reading.readAt)}</Table.Td>
                <Table.Td>{loggedPractitioner(reading.practitioner)}</Table.Td>
                <Table.Td>{reading.readBy ?? 'sin usuario registrado'}</Table.Td>
                <Table.Td>{loggedPatient(reading.patient)}</Table.Td>
                <Table.Td>{READ_ACCESS[reading.access]}</Table.Td>
                <Table.Td>{readingWhat(reading)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Log>
  );
}

/** Who tried to reach a record, a file or a clinical route and was refused (D-72). */
export function RefusalsPage() {
  const search = useAddressSearch(['patient', 'user', 'from', 'to'] as const);
  const found = useQuery({ queryKey: ['record-refusals', search.query],
    queryFn: () => api.get<Refusals>('/api/record-refusals', search.query) });
  return (
    <Log title="Intentos rechazados" about="Quién intentó llegar a una historia clínica, a un archivo o a una ruta clínica sin poder, y cuándo."
      error={found.error} limitReached={found.data?.limitReached} more="Hay más intentos de los que se muestran"
      empty={found.data?.refusals.length === 0 ? 'No hay intentos rechazados con esos datos.' : null}
      form={(
        <form onSubmit={search.submit}>
          <Group align="flex-end">
            <TextInput label="Paciente (nombre, documento o alias)" value={search.typed.patient} onChange={search.type('patient')} />
            <TextInput label="Usuario" value={search.typed.user} onChange={search.type('user')} />
            <Days search={search} answered={found.data} />
          </Group>
        </form>
      )}>
      {found.data && found.data.refusals.length > 0 && (
        <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
          <Table.Thead><Table.Tr><Table.Th>Cuándo</Table.Th><Table.Th>Usuario</Table.Th><Table.Th>Profesional</Table.Th>
            <Table.Th>Paciente</Table.Th><Table.Th>Qué intentó</Table.Th></Table.Tr></Table.Thead>
          <Table.Tbody>
            {found.data.refusals.map((refusal, index) => (
              <Table.Tr key={index}>
                <Table.Td className="mono">{when(refusal.refusedAt)}</Table.Td>
                <Table.Td>{refusal.refusedBy}</Table.Td>
                <Table.Td>{loggedPractitioner(refusal.practitioner)}</Table.Td>
                <Table.Td>{loggedPatient(refusal.patient)}</Table.Td>
                <Table.Td>{refusalWhat(refusal)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Log>
  );
}

/** The cancellations, to follow what the staff does (D-76): never the patient nor reception's note. */
export function CancellationLogPage() {
  const search = useAddressSearch(['practitionerId', 'from', 'to'] as const);
  const practitioners = usePractitioners();
  const found = useQuery({ queryKey: ['cancellation-log', search.query],
    queryFn: () => api.get<CancellationLog>('/api/cancellation-log', search.query) });
  return (
    <Log title="Cancelaciones de turnos"
      about="Quién canceló, cuándo, cómo y quién lo pidió, por el día en que se canceló. Nunca el paciente ni la nota de recepción."
      error={found.error} limitReached={found.data?.limitReached} more="Hay más cancelaciones de las que se muestran"
      empty={found.data?.cancellations.length === 0 ? 'No hay cancelaciones con esos datos.' : null}
      form={(
        <form onSubmit={search.submit}>
          <Group align="flex-end">
            <NativeSelect label="Profesional" value={search.typed.practitionerId} onChange={search.type('practitionerId')}
              data={[{ value: '', label: '— todos —' }, ...practitioners.map((each) => ({ value: each.id, label: each.fullName }))]} />
            <Days search={search} answered={found.data} />
          </Group>
        </form>
      )}>
      {found.data && found.data.cancellations.length > 0 && (
        <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
          <Table.Thead><Table.Tr><Table.Th>Cuándo</Table.Th><Table.Th>Usuario</Table.Th><Table.Th>Cómo</Table.Th>
            <Table.Th>Lo pidió</Table.Th><Table.Th>Turno</Table.Th><Table.Th>Profesional</Table.Th></Table.Tr></Table.Thead>
          <Table.Tbody>
            {found.data.cancellations.map((entry, index) => (
              <Table.Tr key={index}>
                <Table.Td className="mono">{entry.cancelledAt ? when(entry.cancelledAt) : '—'}</Table.Td>
                <Table.Td>{cancelledByForAdmins(entry)}</Table.Td>
                <Table.Td>{CANCELLATION_ORIGINS[entry.origin] ?? entry.origin}</Table.Td>
                <Table.Td>{entry.requestedBy ? REQUESTER[entry.requestedBy] : '—'}</Table.Td>
                <Table.Td className="mono">{when(entry.startsAt)}</Table.Td>
                <Table.Td>{loggedPractitioner(entry.practitioner)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Log>
  );
}

/**
 * A day of the cash desk (D-87, D-88, D-89): each charge and refund by means and who recorded it, and what should be
 * in the drawer. Never the patient nor why something was given back.
 */
export function CashDeskLogPage() {
  const [params, setParams] = useSearchParams();
  const date = params.get('date');
  const day = useQuery({ queryKey: ['cash-desk-log', date], queryFn: () => api.get<CashLog>('/api/cash-desk-log', { date }) });
  if (day.isPending) {
    return <Loader aria-label="Cargando la caja" />;
  }
  if (day.isError) {
    return <Alert color="red" role="alert">{describeError(day.error)}</Alert>;
  }
  return (
    <Stack gap={20}>
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={1} fz={28}>Caja del día</Title>
          <Text c="dimmed" fz={15}>Los cobros y las devoluciones de ese día, por medio de pago, y quién los registró. Lo que queda
            en efectivo es lo que debería haber en el cajón. Nunca el paciente ni el motivo de una devolución.</Text>
        </Stack>
        <TextInput label="Día" type="date" value={day.data.date}
          onChange={(event) => setParams(event.currentTarget.value ? { date: event.currentTarget.value } : {})} />
      </Group>
      <SimpleGrid cols={{ base: 2, md: 4 }}>
        {tiles(day.data.total).map((tile) => (
          <Paper key={tile.label} className="card" p={16}>
            <Text fz={13} c="dimmed">{tile.label}</Text>
            <Text className="mono" fz={22} fw={600}>{tile.amount}</Text>
          </Paper>
        ))}
      </SimpleGrid>
      <Title order={2} fz={20}>Totales</Title>
      <Totals byRecorder={day.data.byRecorder} total={day.data.total} />
      <Title order={2} fz={20}>Movimientos</Title>
      {day.data.movements.length === 0 && <Text c="dimmed">No hay movimientos ese día.</Text>}
      {day.data.movements.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Hora</Table.Th><Table.Th>Movimiento</Table.Th><Table.Th>Medio</Table.Th>
              <Table.Th>Importe</Table.Th><Table.Th>Registró</Table.Th></Table.Tr></Table.Thead>
            <Table.Tbody>
              {day.data.movements.map((movement, index) => (
                <Table.Tr key={index}>
                  <Table.Td className="mono">{timeOf(movement.recordedAt)}</Table.Td>
                  <Table.Td>{movement.kind === 'REFUND' ? 'Devolución' : 'Cobro'}</Table.Td>
                  <Table.Td>{MEANS[movement.means] ?? movement.means}</Table.Td>
                  <Table.Td className="mono">{movement.kind === 'REFUND' ? `−${pesos(movement.amount)}` : pesos(movement.amount)}</Table.Td>
                  <Table.Td>{movement.recordedBy}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
    </Stack>
  );
}

/** The practitioners to filter by; their names alone (D-107). */
function usePractitioners(): Practitioner[] {
  return useQuery({ queryKey: ['practitioners'], queryFn: () => api.get<Practitioner[]>('/api/practitioners') }).data ?? [];
}

/** The days searched: those typed, or else those the server answered — the last month, unless asked for others. */
function Days({ search, answered }: {
  search: ReturnType<typeof useAddressSearch<'from' | 'to'>>;
  answered?: { from: string; to: string };
}) {
  return (
    <>
      <TextInput label="Desde" type="date" value={search.typed.from || answered?.from || ''} onChange={search.type('from')} />
      <TextInput label="Hasta" type="date" value={search.typed.to || answered?.to || ''} onChange={search.type('to')} />
      <Button type="submit">Buscar</Button>
    </>
  );
}

/** One of the admins' logs: its words, its search, a refusal or a warning of more, and its table. */
function Log({ title, about, form, error, limitReached, more, empty, children }: {
  title: string;
  about: string;
  form: ReactNode;
  error: unknown;
  limitReached?: boolean;
  more: string;
  empty: string | null;
  children: ReactNode;
}) {
  return (
    <Stack gap={20}>
      <Stack gap={4}>
        <Title order={1} fz={28}>{title}</Title>
        <Text c="dimmed">{about}</Text>
      </Stack>
      <Paper className="card" p={16}>{form}</Paper>
      {error ? <Alert color="red" role="alert">{describeError(error, LOG_REFUSALS)}</Alert> : null}
      {limitReached && <Alert color="amber" role="alert">{more}: acotá la búsqueda para ver el resto.</Alert>}
      {empty && <Text c="dimmed">{empty}</Text>}
      {children && <section className="card">{children}</section>}
    </Stack>
  );
}
