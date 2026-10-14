import { Alert, Anchor, Loader, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';

import { timeOf, weekdayDay } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { type AgendaAppointment, stateOf } from '../agenda/agenda';
import { api } from '../session';
import { documentOf, type Patient } from '../types';
import { PATIENT_REFUSALS } from '../words';
import { cancellationText } from './appointments';

/** A patient's appointments, cancelled ones included, each saying who cancelled it and why (D-76). */
export function PatientAppointmentsPage() {
  const { id } = useParams();
  const patient = useQuery({ queryKey: ['patient', id], queryFn: () => api.get<Patient>(`/api/patients/${id}`) });
  const appointments = useQuery({ queryKey: ['patient-appointments', id],
    queryFn: () => api.get<AgendaAppointment[]>(`/api/patients/${id}/appointments`) });

  if (patient.isError || appointments.isError) {
    return <Alert color="red" role="alert">{describeError(patient.error ?? appointments.error, PATIENT_REFUSALS)}</Alert>;
  }
  if (!patient.data || !appointments.data) {
    return <Loader aria-label="Cargando los turnos" />;
  }
  return (
    <Stack gap={20}>
      <Stack gap={4}>
        <Title order={1} fz={28}>Turnos de {patient.data.fullName}</Title>
        {documentOf(patient.data) && <Text c="dimmed">{documentOf(patient.data)}</Text>}
      </Stack>
      {appointments.data.length === 0 ? <Text c="dimmed">Este paciente todavía no tiene turnos.</Text> : (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead>
              <Table.Tr><Table.Th>Día</Table.Th><Table.Th>Horario</Table.Th><Table.Th>Profesional</Table.Th>
                <Table.Th>Razón</Table.Th><Table.Th>Estado</Table.Th><Table.Th /></Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {appointments.data.map((appointment) => (
                <Table.Tr key={appointment.id}>
                  <Table.Td>{weekdayDay(appointment.startsAt)}</Table.Td>
                  <Table.Td className="mono">{timeOf(appointment.startsAt)} – {timeOf(appointment.endsAt)}</Table.Td>
                  <Table.Td>{appointment.practitioner}</Table.Td>
                  <Table.Td>
                    {appointment.reason}
                    {appointment.bookedVia === 'WEB' && <Text span c="dimmed"> · Web</Text>}
                    {appointment.movedOnWeb && <Text span c="dimmed"> · Movido por la web</Text>}
                    {appointment.note && <Text fz={13} c="dimmed">{appointment.note}</Text>}
                  </Table.Td>
                  <Table.Td>
                    {stateOf(appointment).label}
                    {appointment.status === 'CANCELLED' && (
                      <Text fz={13} c="dimmed">{cancellationText(appointment.cancellation)}</Text>
                    )}
                    {appointment.cancellation?.note && (
                      <Text fz={13} c="dimmed">Nota: {appointment.cancellation.note}</Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    {appointment.status === 'BOOKED' && (
                      <Anchor component={Link}
                        to={`/agenda?date=${appointment.startsAt.slice(0, 10)}&practitionerId=${appointment.practitionerId}`}>
                        Ver en la agenda
                      </Anchor>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
      <Anchor component={Link} to="/patients">Volver a pacientes</Anchor>
    </Stack>
  );
}
