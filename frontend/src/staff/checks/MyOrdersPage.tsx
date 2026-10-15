import { Alert, Anchor, Loader, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';

import { todayAtThePractice, when } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import '../record/record.css';
import { api } from '../session';
import { type MyOrder, RECENT_DAYS, orderSituation, patientOf, splitOrders } from './lists';

/**
 * The orders the practitioner issued, followed up (D-56, D-105): what waits, and what was reported — from the last 30
 * days, or all of them on asking, in the address. Where each stands, never what its result says; the record, with
 * "Ver historia", only while they may read it.
 */
export function MyOrdersPage() {
  const [params, setParams] = useSearchParams();
  const all = params.get('all') === 'true';
  const orders = useQuery({ queryKey: ['my-orders'], queryFn: () => api.get<MyOrder[]>('/api/orders/mine') });
  const split = orders.data ? splitOrders(orders.data, todayAtThePractice(new Date()), all) : null;
  return (
    <Stack gap={20}>
      <Title order={1} fz={28}>Mis órdenes</Title>
      {orders.isError && <Alert color="red" role="alert">{describeError(orders.error)}</Alert>}
      {orders.isPending && <Loader aria-label="Cargando tus órdenes" />}
      {split && (
        <>
          <Title order={2} fz={20}>Pendientes</Title>
          {split.waiting.length === 0
            ? <Text c="dimmed">No tenés órdenes esperando.</Text>
            : <Orders orders={split.waiting} heading="Situación" />}
          <Title order={2} fz={20}>{all ? 'Con resultado' : `Con resultado en los últimos ${RECENT_DAYS} días`}</Title>
          {split.reported.length === 0
            ? <Text c="dimmed">No hay resultados para mostrar.</Text>
            : <Orders orders={split.reported} heading="Resultado" />}
          <Anchor component="button" type="button" fz={14} style={{ alignSelf: 'flex-start' }}
            onClick={() => setParams(all ? {} : { all: 'true' })}>
            {all ? `Ver solo los últimos ${RECENT_DAYS} días` : 'Ver todos los resultados'}
          </Anchor>
        </>
      )}
    </Stack>
  );
}

function Orders({ orders, heading }: { orders: MyOrder[]; heading: string }) {
  return (
    <section className="card" style={{ overflowX: 'auto' }}>
      <Table className="card-table" miw={720} verticalSpacing={12} horizontalSpacing={14}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Paciente</Table.Th><Table.Th>Orden</Table.Th><Table.Th>Emitida</Table.Th>
            <Table.Th>{heading}</Table.Th><Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {orders.map((order) => (
            <Table.Tr key={order.orderId}>
              <Table.Td>{patientOf(order)}</Table.Td>
              <Table.Td className="notes">{order.description}</Table.Td>
              <Table.Td>{when(order.orderedAt)}</Table.Td>
              <Table.Td>{orderSituation(order)}</Table.Td>
              <Table.Td>
                {order.mayRead && <Anchor component={Link} to={`/clinical-records/${order.patientId}`}>Ver historia</Anchor>}
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </section>
  );
}
