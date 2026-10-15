import { Alert, Button, Loader, Stack, Table, Text, Textarea, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { when } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { CHECK_ACCESS_REFUSALS, grantedUntil, useReasonLabel } from '../record/access';
import { nameOf } from '../record/record';
import '../record/record.css';
import { api } from '../session';
import { type PendingCheck, patientOf } from './lists';

/**
 * The checks of the practice waiting for their result (D-54, D-105): only the patient and the check — nothing else of
 * the record, and no reading of it (D-59) —, with "Ver historia" for a record the practitioner may read, and asking for
 * access to report it otherwise; granted, it leads to the record.
 */
export function PendingChecksPage() {
  const pending = useQuery({ queryKey: ['pending-checks'], queryFn: () => api.get<PendingCheck[]>('/api/checks/pending') });
  const navigate = useNavigate();
  return (
    <Stack gap={20}>
      <Stack gap={4}>
        <Title order={1} fz={28}>Estudios pendientes</Title>
        <Text c="dimmed" fz={15}>
          Los estudios del consultorio que esperan su resultado. Para cargar uno hace falta acceso a la historia del
          paciente; si no lo tenés, podés pedirlo desde acá.
        </Text>
      </Stack>
      {pending.isError && <Alert color="red" role="alert">{describeError(pending.error)}</Alert>}
      {pending.isPending && <Loader aria-label="Cargando los estudios pendientes" />}
      {pending.data?.length === 0 && <Text c="dimmed">No hay resultados pendientes.</Text>}
      {!!pending.data?.length && (
        <section className="card" style={{ overflowX: 'auto' }}>
          <Table className="card-table" miw={720} verticalSpacing={12} horizontalSpacing={14}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Paciente</Table.Th><Table.Th>Estudio</Table.Th><Table.Th>Realizado</Table.Th><Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {pending.data.map((check) => (
                <Table.Tr key={check.checkId}>
                  <Table.Td>{patientOf(check)}</Table.Td>
                  <Table.Td className="notes">{check.description}</Table.Td>
                  <Table.Td>
                    {when(check.performedAt)}
                    <Text fz={13} c="dimmed">{nameOf(check.performedByName)}</Text>
                  </Table.Td>
                  <Table.Td style={{ minWidth: 240 }}>
                    {check.mayRead
                      ? <Button onClick={() => navigate(`/clinical-records/${check.patientId}`)}>Ver historia</Button>
                      : <AskAccess check={check} />}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
    </Stack>
  );
}

function AskAccess({ check }: { check: PendingCheck }) {
  const label = useReasonLabel();
  const navigate = useNavigate();
  const [reason, setReason] = useState('');
  const ask = useAction(
    (why: string) => api.post<{ validUntil: string }>(`/api/checks/${check.checkId}/access-grants`,
      { reason: why.trim() ? why : null }),
    {
      refusals: CHECK_ACCESS_REFUSALS,
      onDone: (grant) => navigate(`/clinical-records/${check.patientId}`,
        { state: { notice: grantedUntil(grant.validUntil) } }),
    },
  );
  return (
    <form onSubmit={(event) => { event.preventDefault(); ask.run(reason); }}>
      <Stack gap={6}>
        <Textarea label={label} value={reason} onChange={(event) => setReason(event.currentTarget.value)}
          rows={2} maxLength={500} />
        <Button type="submit" variant="default" loading={ask.pending}>Pedir acceso</Button>
        <Said error={ask.error} />
      </Stack>
    </form>
  );
}
