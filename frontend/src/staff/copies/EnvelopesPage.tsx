import { Button, Checkbox, Group, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { shortDay, timeOf } from '../../shared/dates';
import { byCode } from '../../shared/refusals';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';

/** A copy on paper as reception sees it (D-95): whose, who asked for it, since when it waits — never what it holds. */
export interface Envelope {
  id: string;
  patient: Person;
  requestedBy: Person;
  authenticatedAt: string;
}

interface Person {
  id: string;
  fullName: string | null;
  document: string | null;
}

const GONE = 'Esa copia ya fue entregada.';

export const ENVELOPE_REFUSALS = byCode({
  DOCUMENT_NOT_SEEN: 'Marcá que viste el documento de quien la retira.',
  ALREADY_HANDED_OVER: GONE,
  RECORD_COPY_NOT_FOUND: GONE,
  RECORD_COPY_CHANGED: GONE,
});

/** `Ana Pérez — DNI 30111222`, as the envelope names whose it is. */
export function personOf(person: Person): string {
  if (!person.fullName) return '(paciente desconocido)';
  return person.document ? `${person.fullName} — ${person.document}` : person.fullName;
}

/** Who asked for it: the patient, or whoever of their family group asked for them (D-95). */
export function askedBy(envelope: Envelope): string {
  return envelope.requestedBy.id === envelope.patient.id ? 'El paciente' : personOf(envelope.requestedBy);
}

/**
 * "Copias para retirar" (D-95): the copies on paper, authenticated and left in a sealed envelope, handed over to
 * whoever asked for them, their document seen. Never what they hold, nor who authenticated them (D-51).
 */
export function EnvelopesPage() {
  const envelopes = useQuery({ queryKey: ['record-copy-envelopes'], queryFn: () => api.get<Envelope[]>('/api/record-copy-envelopes') });
  const [seen, setSeen] = useState<Record<string, boolean>>({});
  const handOver = useAction((id: string) => api.post(`/api/record-copy-envelopes/${id}/handover`, { documentSeen: Boolean(seen[id]) }), {
    refusals: ENVELOPE_REFUSALS, invalidates: ['record-copy-envelopes'], done: 'Copia entregada.',
  });
  return (
    <Stack gap={20} maw={960}>
      <Stack gap={4}>
        <Title order={1} fz={28}>Copias para retirar</Title>
        <Text c="dimmed">Copias de la historia clínica en papel, en sobre cerrado. Se entregan a quien las pidió, con su
          documento a la vista.</Text>
      </Stack>
      <Said error={handOver.error} notice={handOver.notice} />
      {envelopes.data?.length === 0 && <Text c="dimmed">No hay copias para retirar.</Text>}
      {envelopes.data && envelopes.data.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Paciente</Table.Th><Table.Th>La pidió</Table.Th><Table.Th>Lista desde</Table.Th>
              <Table.Th /></Table.Tr></Table.Thead>
            <Table.Tbody>
              {envelopes.data.map((envelope) => (
                <Table.Tr key={envelope.id}>
                  <Table.Td>{personOf(envelope.patient)}</Table.Td>
                  <Table.Td>{askedBy(envelope)}</Table.Td>
                  <Table.Td className="mono">{shortDay(envelope.authenticatedAt)} {timeOf(envelope.authenticatedAt)}</Table.Td>
                  <Table.Td>
                    <Group gap={12} wrap="nowrap">
                      <Checkbox label="Vi el documento de quien la retira" checked={Boolean(seen[envelope.id])}
                        onChange={(event) => { const { checked } = event.currentTarget; setSeen((before) => ({ ...before, [envelope.id]: checked })); }} />
                      <Button size="xs" loading={handOver.pending} onClick={() => handOver.run(envelope.id)}
                        aria-label={`Entregar la copia de ${personOf(envelope.patient)}`}>
                        Entregar
                      </Button>
                    </Group>
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
