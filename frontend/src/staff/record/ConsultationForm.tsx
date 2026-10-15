import { Button, Group, Stack, Text, Textarea } from '@mantine/core';
import { type ReactNode, useEffect, useState } from 'react';

import { newId } from '../../shared/ids';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import {
  CHECK_REFUSALS, CONSULTATION_REFUSALS, type CheckRequest, type CheckRow, type ConsultationRequest, type Draft, EMPTY_CHECK, checkRequest,
  isTouched, newDraft, toRequest,
} from './consultation';
import { OrderChoice } from './parts';
import type { Order } from './record';

/**
 * A new consultation (D-47, D-51, D-55), as the mock has it: what was consulted, and its prescriptions, checks and
 * orders added as rows when needed. Sent once, whole; it cannot be changed afterwards. What was typed lives in this
 * screen's memory alone, and a refusal leaves it as it was (D-52, D-97).
 */
export function ConsultationForm({ patientId, openOrders, onTouched }: {
  patientId: string; openOrders: Order[]; onTouched?: (touched: boolean) => void;
}) {
  const [draft, setDraft] = useState<Draft>(() => newDraft(newId()));
  const [problem, setProblem] = useState<string | null>(null);
  const send = useAction((request: ConsultationRequest) => api.post('/api/consultations', request), {
    refusals: CONSULTATION_REFUSALS, done: 'Consulta registrada.', invalidates: ['record'],
    onDone: () => setDraft(newDraft(newId())),
  });
  const touched = isTouched(draft);
  useEffect(() => onTouched?.(touched), [onTouched, touched]);

  const change = (next: Partial<Draft>) => setDraft((current) => ({ ...current, ...next }));
  const setAt = <T,>(list: T[], index: number, value: T) => list.map((each, i) => (i === index ? value : each));
  const without = <T,>(list: T[], index: number) => list.filter((_, i) => i !== index);

  return (
    <form className="record-new" onSubmit={(event) => {
      event.preventDefault();
      const built = toRequest(draft, patientId);
      if ('problem' in built) {
        setProblem(built.problem);
        return;
      }
      setProblem(null);
      send.run(built.request);
    }}>
      <Stack gap={12}>
        <Text fw={700}>Nueva consulta</Text>
        <Textarea label="Motivo y evolución" placeholder="Qué trajo al paciente, qué encontraste…" value={draft.notes}
          onChange={(event) => change({ notes: event.currentTarget.value })} rows={4} resize="vertical" maxLength={10000} />

        {draft.prescriptions.map((text, index) => (
          <Row key={`p${index}`} onRemove={() => change({ prescriptions: without(draft.prescriptions, index) })}
            label={`Quitar la receta ${index + 1}`}>
            <Textarea label={`Receta ${index + 1}`} value={text} rows={1} resize="vertical" maxLength={2000}
              onChange={(event) => change({ prescriptions: setAt(draft.prescriptions, index, event.currentTarget.value) })} />
          </Row>
        ))}
        {draft.checks.map((row, index) => (
          <Row key={`c${index}`} onRemove={() => change({ checks: without(draft.checks, index) })}
            label={`Quitar el estudio ${index + 1}`}>
            <CheckFields row={row} openOrders={openOrders} title={`Estudio ${index + 1}`}
              onChange={(next) => change({ checks: setAt(draft.checks, index, next) })} />
          </Row>
        ))}
        {draft.orders.map((text, index) => (
          <Row key={`o${index}`} onRemove={() => change({ orders: without(draft.orders, index) })}
            label={`Quitar la orden ${index + 1}`}>
            <Textarea label={`Orden ${index + 1}: qué se pide`} value={text} rows={1} resize="vertical" maxLength={2000}
              onChange={(event) => change({ orders: setAt(draft.orders, index, event.currentTarget.value) })} />
          </Row>
        ))}

        <Group justify="space-between" gap={8}>
          <Group gap={8}>
            <Button variant="default" onClick={() => change({ prescriptions: [...draft.prescriptions, ''] })}>+ Receta</Button>
            <Button variant="default" onClick={() => change({ checks: [...draft.checks, EMPTY_CHECK] })}>+ Estudio</Button>
            <Button variant="default" onClick={() => change({ orders: [...draft.orders, ''] })}>+ Orden</Button>
          </Group>
          <Button type="submit" loading={send.pending}>Guardar consulta</Button>
        </Group>
        <Text fz={13} c="dimmed">
          Un estudio sin resultado queda pendiente y se carga después. Las órdenes son los estudios que pedís y no hacés
          vos. Una vez guardada, la consulta no se puede modificar.
        </Text>
        <Said error={problem ?? send.error} notice={send.notice} />
      </Stack>
    </form>
  );
}

function Row({ children, onRemove, label }: { children: ReactNode; onRemove: () => void; label: string }) {
  return (
    <Group align="flex-end" gap={8} wrap="nowrap">
      <div style={{ flex: 1 }}>{children}</div>
      <Button variant="subtle" color="gray" aria-label={label} onClick={onRemove}>Quitar</Button>
    </Group>
  );
}

function CheckFields({ row, openOrders, title, onChange }: {
  row: CheckRow; openOrders: Order[]; title: string; onChange: (row: CheckRow) => void;
}) {
  return (
    <Stack gap={6}>
      <Textarea label={`${title}: qué se hizo`} value={row.description} rows={1} resize="vertical" maxLength={2000}
        onChange={(event) => onChange({ ...row, description: event.currentTarget.value })} />
      <OrderChoice orders={openOrders} value={row.orderId} onChange={(orderId) => onChange({ ...row, orderId })} />
      <Textarea label="Resultado (vacío si queda pendiente)" value={row.result} rows={1} resize="vertical" maxLength={10000}
        onChange={(event) => onChange({ ...row, result: event.currentTarget.value })} />
    </Stack>
  );
}

/** A check done without a consultation: the precaution for an emergency (D-53). */
export function LooseCheckForm({ patientId, openOrders }: { patientId: string; openOrders: Order[] }) {
  const [open, setOpen] = useState(false);
  const [row, setRow] = useState<CheckRow>(EMPTY_CHECK);
  const [id, setId] = useState(newId);
  const [problem, setProblem] = useState<string | null>(null);
  const send = useAction((body: CheckRequest) => api.post('/api/checks', body), {
    refusals: CHECK_REFUSALS, done: 'Estudio registrado.', invalidates: ['record'],
    onDone: () => { setRow(EMPTY_CHECK); setId(newId()); setOpen(false); },
  });
  if (!open) {
    return (
      <Stack gap={6} align="flex-start">
        <Button variant="default" onClick={() => setOpen(true)}>Registrar un estudio sin consulta</Button>
        <Said notice={send.notice} />
      </Stack>
    );
  }
  return (
    <form className="record-new" onSubmit={(event) => {
      event.preventDefault();
      const built = checkRequest(row, patientId, id);
      if ('problem' in built) {
        setProblem(built.problem);
        return;
      }
      setProblem(null);
      send.run(built.request);
    }}>
      <Stack gap={10}>
        <Text fw={700}>Estudio sin consulta</Text>
        <CheckFields row={row} openOrders={openOrders} title="Estudio" onChange={setRow} />
        <Group gap={8}>
          <Button type="submit" loading={send.pending}>Registrar estudio</Button>
          <Button variant="subtle" onClick={() => setOpen(false)}>Cerrar</Button>
        </Group>
        <Said error={problem ?? send.error} />
      </Stack>
    </form>
  );
}
