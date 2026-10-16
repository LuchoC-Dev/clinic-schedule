import { Alert, Button, Group, Loader, Paper, SimpleGrid, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type ReactNode, useState } from 'react';
import { useSearchParams } from 'react-router';

import { timeOf } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { pesos } from '../../shared/money';
import { ChargeDialog } from '../components/ChargeDialog';
import { Said } from '../../shared/Said';
import { api } from '../session';
import { CASH_REFUSALS, MEANS } from '../words';
import { type CashDay, cashPatient, coveredInsurance, movementKind, signedAmount, tiles } from './cash';
import { CoverDialog, RefundDialog, WithdrawDialog } from './dialogs';
import { Totals } from './Totals';

/**
 * The day's cash desk (D-87, D-88, D-89, D-90): what was charged and given back, by means and by who recorded it, with
 * what should be in the drawer; each charge given back, some or all, saying why, and a visit given back whole charged
 * again; the emergencies of the day still to charge or cover; and the visits covered, a coverage marked by mistake
 * withdrawn and the visit then charged privately. A very large amount is confirmed a second time (D-101).
 */
export function CashDeskPage() {
  const [params, setParams] = useSearchParams();
  const date = params.get('date');
  const day = useQuery({ queryKey: ['cash-desk', date], queryFn: () => api.get<CashDay>('/api/cash-desk', { date }) });
  const [dialog, setDialog] = useState<ReactNode>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const close = () => setDialog(null);
  const said = (text: string) => () => setNotice(text);

  if (day.isPending) {
    return <Loader aria-label="Cargando la caja" />;
  }
  if (day.isError) {
    return <Alert color="red" role="alert">{describeError(day.error, CASH_REFUSALS)}</Alert>;
  }
  const charge = (visitId: string, about: string, title: string, done: string) => setDialog(
    <ChargeDialog title={title} about={about} button="Cobrar" withCoverage={false} refusals={CASH_REFUSALS}
      invalidates={['cash-desk']} onClose={close} onDone={said(done)}
      send={({ amount, means }) => api.post(`/api/visits/${visitId}/charge`, { amount, means })} />,
  );

  return (
    <Stack gap={20}>
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={1} fz={28}>Caja del día</Title>
          <Text c="dimmed" fz={15}>Lo cobrado y lo devuelto ese día, por medio de pago. Lo que queda en efectivo es lo que
            debería haber en el cajón; una devolución sale por el medio de su cobro. La factura se sigue haciendo aparte.</Text>
        </Stack>
        <TextInput label="Día" type="date" value={day.data.date}
          onChange={(event) => { setNotice(null); setParams(event.currentTarget.value ? { date: event.currentTarget.value } : {}); }} />
      </Group>
      <Said notice={notice} />

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
      {day.data.movements.length === 0 ? <Text c="dimmed">No hay movimientos ese día.</Text> : (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Hora</Table.Th><Table.Th>Movimiento</Table.Th><Table.Th>Paciente</Table.Th>
              <Table.Th>Medio</Table.Th><Table.Th>Importe</Table.Th><Table.Th>Registró</Table.Th><Table.Th /></Table.Tr></Table.Thead>
            <Table.Tbody>
              {day.data.movements.map((movement, index) => (
                <Table.Tr key={index}>
                  <Table.Td className="mono">{timeOf(movement.recordedAt)}</Table.Td>
                  <Table.Td>{movementKind(movement)}{movement.reason && <Text span c="dimmed"> — {movement.reason}</Text>}</Table.Td>
                  <Table.Td>{cashPatient(movement.patient)}{movement.emergency && <Text span c="dimmed"> (urgencia)</Text>}</Table.Td>
                  <Table.Td>{MEANS[movement.means] ?? movement.means}</Table.Td>
                  <Table.Td className="mono">{signedAmount(movement)}</Table.Td>
                  <Table.Td>{movement.recordedBy}</Table.Td>
                  <Table.Td>
                    <Group gap={6} justify="flex-end">
                      {movement.refundable && movement.left !== null && movement.left > 0 && (
                        <Button size="xs" variant="default" onClick={() => setDialog(
                          <RefundDialog visitId={movement.visitId} about={cashPatient(movement.patient)} left={movement.left ?? 0}
                            onClose={close} onDone={said('Devolución registrada.')} />)}>
                          Devolver (queda {pesos(movement.left)})
                        </Button>
                      )}
                      {movement.chargeableAgain && (
                        <Button size="xs" variant="default"
                          onClick={() => charge(movement.visitId, cashPatient(movement.patient), 'Cobrar de nuevo', 'Cobro registrado.')}>
                          Cobrar de nuevo
                        </Button>
                      )}
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}

      <Title order={2} fz={20}>Urgencias sin cobrar</Title>
      <Text c="dimmed" fz={14}>Entraron sin cobro ese día. Se cobran, o se registran con cobertura, cuando se pueda, o nunca.</Text>
      {day.data.pending.length === 0 ? <Text c="dimmed">No hay urgencias pendientes de cobro.</Text> : (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Llegó</Table.Th><Table.Th>Paciente</Table.Th><Table.Th /></Table.Tr></Table.Thead>
            <Table.Tbody>
              {day.data.pending.map((pending) => (
                <Table.Tr key={pending.visitId}>
                  <Table.Td className="mono">{timeOf(pending.arrivedAt)}</Table.Td>
                  <Table.Td>{cashPatient(pending.patient)}</Table.Td>
                  <Table.Td>
                    <Group gap={6} justify="flex-end">
                      <Button size="xs" onClick={() => charge(pending.visitId, cashPatient(pending.patient), 'Cobrar urgencia',
                        'Cobro registrado.')}>Cobrar</Button>
                      <Button size="xs" variant="default" onClick={() => setDialog(
                        <CoverDialog visitId={pending.visitId} about={cashPatient(pending.patient)} onClose={close}
                          onDone={said('Cobertura registrada.')} />)}>Con cobertura</Button>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}

      <Title order={2} fz={20}>Con cobertura</Title>
      <Text c="dimmed" fz={14}>Las visitas que quedaron cubiertas por la obra social ese día; no son dinero. Una marcada por
        error se corrige quitándole la cobertura, después de devolver el coseguro si lo hubo, y se cobra como particular.</Text>
      {day.data.covered.length === 0 ? <Text c="dimmed">No hay visitas con cobertura ese día.</Text> : (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Hora</Table.Th><Table.Th>Paciente</Table.Th><Table.Th>Obra social</Table.Th>
              <Table.Th>Token</Table.Th><Table.Th>Registró</Table.Th><Table.Th /></Table.Tr></Table.Thead>
            <Table.Tbody>
              {day.data.covered.map((covered) => (
                <Table.Tr key={covered.visitId}>
                  <Table.Td className="mono">{timeOf(covered.recordedAt)}</Table.Td>
                  <Table.Td>{cashPatient(covered.patient)}{covered.emergency && <Text span c="dimmed"> (urgencia)</Text>}</Table.Td>
                  <Table.Td>
                    {coveredInsurance(covered)}
                    {covered.withdrawal && (
                      <Text fz={13} c="dimmed">— cobertura quitada: {covered.withdrawal.reason} ({covered.withdrawal.recordedBy})</Text>
                    )}
                  </Table.Td>
                  <Table.Td>{covered.token ?? '—'}</Table.Td>
                  <Table.Td>{covered.recordedBy}</Table.Td>
                  <Table.Td>
                    <Group gap={6} justify="flex-end">
                      {!covered.withdrawal && (
                        <Button size="xs" color="red" variant="light" onClick={() => setDialog(
                          <WithdrawDialog visitId={covered.visitId} about={cashPatient(covered.patient)} onClose={close}
                            onDone={said('Cobertura quitada. Cobrá la visita como particular.')} />)}>Quitar cobertura</Button>
                      )}
                      {covered.chargeable && (
                        <Button size="xs" onClick={() => charge(covered.visitId, cashPatient(covered.patient), 'Cobrar como particular',
                          'Cobro registrado.')}>Cobrar</Button>
                      )}
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
      {dialog}
    </Stack>
  );
}
