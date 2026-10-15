import { Alert, Button, Group, Loader, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { shortDay, timeOf } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import { CODE_REFUSALS, type RecordCopy, askedBy, deliveryOf, personOf, timeLeft } from './copies';

/**
 * "Copias pedidas" (D-95, D-106): the copies of a record the patients asked for, waiting for a practitioner, the
 * soonest due first, with the time left of the 48 hours — only who and when, no reading —; and a copy found by the code
 * printed on it, to check one against the application.
 */
export function RecordCopiesPage() {
  const copies = useQuery({ queryKey: ['record-copies'], queryFn: () => api.get<RecordCopy[]>('/api/record-copies') });
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const find = useAction((typed: string) => api.get<RecordCopy>('/api/record-copies', { code: typed }), {
    refusals: CODE_REFUSALS, onDone: (copy) => navigate(`/record-copies/${copy.id}`),
  });
  return (
    <Stack gap={20}>
      <Stack gap={4}>
        <Title order={1} fz={28}>Copias pedidas</Title>
        <Text c="dimmed" fz={15}>
          Las copias de la historia clínica que pidieron los pacientes desde su cuenta. La ley da 48 horas para
          entregarlas: un profesional que puede leer la historia la revisa, sube lo que falte y la autentica.
        </Text>
      </Stack>
      {copies.isError && <Alert color="red" role="alert">{describeError(copies.error)}</Alert>}
      {copies.isPending && <Loader aria-label="Cargando las copias pedidas" />}
      {copies.data?.length === 0 && <Text c="dimmed">No hay copias esperando.</Text>}
      {!!copies.data?.length && (
        <section className="card" style={{ overflowX: 'auto' }}>
          <Table className="card-table" miw={720} verticalSpacing={12} horizontalSpacing={14}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Paciente</Table.Th><Table.Th>Pedida</Table.Th><Table.Th>Entrega</Table.Th>
                <Table.Th>Plazo</Table.Th><Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {copies.data.map((copy) => (
                <Table.Tr key={copy.id}>
                  <Table.Td>
                    {personOf(copy.patient)}
                    <Text fz={13} c="dimmed">Pidió: {askedBy(copy)}</Text>
                  </Table.Td>
                  <Table.Td>{shortDay(copy.requestedAt)} {timeOf(copy.requestedAt)}</Table.Td>
                  <Table.Td>{deliveryOf(copy)}</Table.Td>
                  <Table.Td c={copy.late ? 'red' : undefined} fw={copy.late ? 600 : undefined}>
                    {timeLeft(copy.minutesLeft)}
                  </Table.Td>
                  <Table.Td>
                    <Button variant={copy.mayRead ? 'filled' : 'default'}
                      onClick={() => navigate(`/record-copies/${copy.id}`)}>
                      {copy.mayRead ? 'Revisar' : 'Ver pedido'}
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}

      <Title order={2} fz={20}>Verificar una copia</Title>
      <form onSubmit={(event) => { event.preventDefault(); if (code.trim()) find.run(code.trim()); }}>
        <Group align="flex-end" gap={8}>
          <TextInput label="Código de verificación" placeholder="K7QM-3XPA-9TRD" maxLength={20} value={code}
            onChange={(event) => setCode(event.currentTarget.value)} />
          <Button type="submit" variant="default" loading={find.pending}>Buscar</Button>
        </Group>
      </form>
      <Said error={find.error} />
    </Stack>
  );
}
