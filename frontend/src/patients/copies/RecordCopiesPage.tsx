import { Anchor, Badge, Button, Group, Loader, NativeSelect, Paper, Radio, Stack, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';

import { describeError } from '../../shared/errors';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { ASKED, COPY_REFUSALS, type Copies, copyRow, type Delivery } from './copies';

/**
 * *Mi historia clínica* (D-95), as the approved mock has it: asking for a copy of the whole record — one's own, or a
 * child's under 13 —, how to get it, and each copy asked for, with its PDF to download once authenticated. Nothing of
 * the record is read here: it comes as the copy, downloaded with a plain link, kept nowhere by the application (D-51,
 * D-96).
 */
export function RecordCopiesPage() {
  const copies = useQuery({ queryKey: ['record-copies'], gcTime: 0,
    queryFn: () => api.get<Copies>('/api/portal/record-copies') });
  const [patient, setPatient] = useState<string | null>(null);
  const [delivery, setDelivery] = useState<Delivery | null>(null);
  const [unchosen, setUnchosen] = useState(false);
  const whom = copies.data?.askableFor ?? [];
  const chosen = patient ?? whom[0]?.id ?? null;
  const ask = useAction(() => api.post('/api/portal/record-copies', { patientId: chosen, delivery }), {
    refusals: COPY_REFUSALS, done: ASKED, invalidates: ['record-copies'],
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setUnchosen(delivery === null);
    if (delivery !== null) {
      ask.run(undefined);
    }
  }

  if (copies.isError) {
    return <Said error={describeError(copies.error)} />;
  }
  if (!copies.data) {
    return <Loader size="sm" aria-label="Buscando tus copias" />;
  }
  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>Mi historia clínica</Title>
      <Text fz={15}>Podés pedir una copia completa de tu historia clínica: todas las consultas, chequeos, resultados y
        archivos, de todos los profesionales. Un profesional la revisa y la autentica dentro de las 48 horas (Ley 26.529,
        art. 14).</Text>
      <Said error={unchosen ? 'Elegí cómo querés recibir la copia.' : ask.error} notice={unchosen ? null : ask.notice} />

      {copies.data.copies.map((copy) => {
        const row = copyRow(copy);
        return (
          <Paper key={copy.id} className="card" p={16} component="article" aria-label={`${row.whose}, ${row.requestedAt}`}>
            <Stack gap={6}>
              <Group justify="space-between" wrap="nowrap">
                <Text fw={700}>{row.whose}</Text>
                <Badge color={row.downloadable ? 'petrol' : 'gray'} variant="light">{row.delivery}</Badge>
              </Group>
              <Text fz={14} c="dimmed">Pedida el {row.requestedAt}</Text>
              <Text fz={15}>{row.state}</Text>
              {row.downloadable && (
                <Anchor href={`/api/portal/record-copies/${copy.id}/pdf`} fw={600} fz={15}>Descargar PDF</Anchor>
              )}
            </Stack>
          </Paper>
        );
      })}
      {copies.data.copies.length === 0 && <Text c="dimmed">Todavía no pediste ninguna copia.</Text>}

      {whom.length === 0 ? (
        <Text c="dimmed">Para pedir una copia desde la web, acercate a recepción.</Text>
      ) : (
        <form onSubmit={submit}>
          <Stack gap={12}>
            <Title order={2} fz={18}>Pedir una copia nueva</Title>
            {whom.length > 1 && (
              <NativeSelect label="De quién" size="md" value={chosen ?? ''} onChange={(event) => setPatient(event.currentTarget.value)}
                data={whom.map((each) => ({ value: each.id, label: each.fullName }))} />
            )}
            <Radio.Group label="Cómo la querés recibir" value={delivery} onChange={(value) => setDelivery(value as Delivery)}>
              <Stack gap={10} mt={8}>
                <Radio value="PDF" label="En PDF, para descargar desde acá" />
                <Radio value="PAPER" label="En papel, firmada y sellada: la retirás en recepción con tu documento" />
              </Stack>
            </Radio.Group>
            <Button type="submit" size="md" loading={ask.pending}>Pedir copia</Button>
          </Stack>
        </form>
      )}
    </Stack>
  );
}
