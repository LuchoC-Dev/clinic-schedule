import { Table } from '@mantine/core';

import { pesos } from '../../shared/money';
import { MEANS } from '../words';
import type { Total } from './cash';

/**
 * What each user recorded and everyone together, each by means under it; only cash counts for the drawer (D-89).
 * Reception's cash desk and the admins' log of it show it alike.
 */
export function Totals({ byRecorder, total }: { byRecorder: (Total & { recordedBy: string })[]; total: Total }) {
  const rows = (who: string, sum: Total, strong: boolean) => [
    <Table.Tr key={who} fw={strong ? 700 : undefined}>
      <Table.Td>{who}</Table.Td>
      <Table.Td className="mono">{sum.charged.count}</Table.Td>
      <Table.Td className="mono">{pesos(sum.charged.amount)}</Table.Td>
      <Table.Td className="mono">{sum.refunded.count}</Table.Td>
      <Table.Td className="mono">{pesos(sum.refunded.amount)}</Table.Td>
      <Table.Td className="mono">{pesos(sum.left)}</Table.Td>
      <Table.Td className="mono">{pesos(sum.drawer)}</Table.Td>
    </Table.Tr>,
    ...sum.byMeans.map((means) => (
      <Table.Tr key={`${who}-${means.means}`} c="dimmed">
        <Table.Td>· {MEANS[means.means] ?? means.means}</Table.Td>
        <Table.Td className="mono">{means.charged.count}</Table.Td>
        <Table.Td className="mono">{pesos(means.charged.amount)}</Table.Td>
        <Table.Td className="mono">{means.refunded.count}</Table.Td>
        <Table.Td className="mono">{pesos(means.refunded.amount)}</Table.Td>
        <Table.Td className="mono">{pesos(means.left)}</Table.Td>
        <Table.Td />
      </Table.Tr>
    )),
  ];
  return (
    <section className="card">
      <Table className="card-table" verticalSpacing={8} horizontalSpacing={14}>
        <Table.Thead><Table.Tr><Table.Th>Registró</Table.Th><Table.Th>Cobros</Table.Th><Table.Th>Cobrado</Table.Th>
          <Table.Th>Devoluciones</Table.Th><Table.Th>Devuelto</Table.Th><Table.Th>Queda</Table.Th><Table.Th>En el cajón</Table.Th>
        </Table.Tr></Table.Thead>
        <Table.Tbody>
          {byRecorder.flatMap((recorder) => rows(recorder.recordedBy, recorder, false))}
          {rows('Total', total, true)}
        </Table.Tbody>
      </Table>
    </section>
  );
}
