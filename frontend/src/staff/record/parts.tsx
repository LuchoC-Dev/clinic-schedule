import { Anchor, Button, FileButton, FileInput, Group, Select, Stack, Text, Textarea } from '@mantine/core';
import { useState } from 'react';

import { when } from '../../shared/dates';
import { newId } from '../../shared/ids';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { ADDENDUM_REFUSALS, CANCEL_ORDER_REFUSALS, FILE_REFUSALS, RESULT_REFUSALS } from './consultation';
import {
  type Addendum, type Attachment, type Check, type ClinicalRecord, type Order, filesOf, mayCancel, nameOf, orderState,
  resultOf, sizeOf,
} from './record';

/** What a file input takes: what the record keeps (D-57). */
export const ACCEPTED_FILES = '.pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg';

/**
 * One attached file: downloaded through the API, only by whoever may read the record, and recorded as a reading
 * (D-57, D-59). A plain link: the answer is `no-store`, so the browser keeps no copy of its own.
 */
export function FileLink({ file }: { file: Attachment }) {
  return (
    <Text fz={14}>
      <Anchor href={`/api/attachments/${file.id}`}>{file.fileName}</Anchor>
      <Text component="span" c="dimmed" fz={13}>
        {' '}({sizeOf(file.sizeBytes)}) — {nameOf(file.uploadedByName)}, {when(file.uploadedAt)}
      </Text>
    </Text>
  );
}

export function FileList({ files }: { files: Attachment[] }) {
  if (files.length === 0) {
    return null;
  }
  return <Stack gap={2} component="ul" className="record-files">{files.map((file) => <li key={file.id}><FileLink file={file} /></li>)}</Stack>;
}

/** Attaches one file, at once, where it is chosen (D-57, D-94). */
export function AttachButton({ path, label = 'Adjuntar archivo', done = 'Archivo adjuntado.' }: {
  path: string; label?: string; done?: string;
}) {
  const attach = useAction((file: File) => api.upload(path, file),
    { refusals: FILE_REFUSALS, done, invalidates: ['record'] });
  return (
    <Stack gap={6} align="flex-start">
      <FileButton onChange={(file) => file && attach.run(file)} accept={ACCEPTED_FILES}>
        {(props) => <Button {...props} variant="default" size="xs" loading={attach.pending}>{label}</Button>}
      </FileButton>
      <Said error={attach.error} notice={attach.notice} />
    </Stack>
  );
}

function AddendumList({ addenda }: { addenda: Addendum[] }) {
  return addenda.map((note) => (
    <div key={note.id} className="record-addendum">
      <Text fz={13} c="dimmed">Aclaración — {when(note.writtenAt)} — {nameOf(note.practitionerName)}</Text>
      <Text fz={14} className="notes">{note.text}</Text>
    </div>
  ));
}

/** Addenda written afterwards, never changing what was written (D-52, D-58), and the form to add one. */
export function Addenda({ addenda, path, label }: { addenda: Addendum[]; path: string; label: string }) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [id, setId] = useState(newId);
  const [problem, setProblem] = useState<string | null>(null);
  const add = useAction((body: { id: string; text: string }) => api.post(path, body), {
    refusals: ADDENDUM_REFUSALS, done: 'Aclaración agregada.', invalidates: ['record'],
    onDone: () => { setText(''); setId(newId()); setOpen(false); },
  });
  return (
    <Stack gap={6}>
      <AddendumList addenda={addenda} />
      {open ? (
        <form onSubmit={(event) => {
          event.preventDefault();
          if (!text.trim()) {
            setProblem('Escribí la aclaración.');
            return;
          }
          setProblem(null);
          add.run({ id, text });
        }}>
          <Stack gap={6}>
            <Textarea label={label} value={text} onChange={(event) => setText(event.currentTarget.value)}
              rows={2} resize="vertical" maxLength={2000} />
            <Group gap={8}>
              <Button type="submit" size="xs" loading={add.pending}>Agregar aclaración</Button>
              <Button variant="subtle" size="xs" onClick={() => setOpen(false)}>Cerrar</Button>
            </Group>
          </Stack>
        </form>
      ) : (
        <Group><Button variant="subtle" size="xs" px={0} onClick={() => setOpen(true)}>Agregar aclaración</Button></Group>
      )}
      <Said error={problem ?? add.error} notice={add.notice} />
    </Stack>
  );
}

/**
 * One check: what was done, by whom and when, and its result — or, while pending, the form for it, in words, in a
 * file, or both (D-53, D-57) —; its files, and its addenda, how a wrong result is corrected (D-58).
 */
export function CheckBlock({ check, record }: { check: Check; record: ClinicalRecord }) {
  const result = resultOf(check);
  return (
    <div className="record-check">
      <Text fz={13} c="dimmed">Estudio — {when(check.performedAt)} — {nameOf(check.performedByName)}</Text>
      <Text fz={15} className="notes">{check.description}</Text>
      {result.pending
        ? <><Text fz={14} fw={600}>{result.text}</Text><ResultForm check={check} /></>
        : (
          <Text fz={14}>
            <strong>Resultado:</strong> <span className="notes">{result.text}</span>
            <Text component="span" c="dimmed" fz={13}> — {result.by}</Text>
          </Text>
        )}
      <FileList files={filesOf(record, 'CHECK', check.id)} />
      {!result.pending && <AttachButton path={`/api/checks/${check.id}/attachments`} />}
      <Addenda addenda={check.addenda} path={`/api/checks/${check.id}/addenda`} label="Aclaración sobre el estudio" />
    </div>
  );
}

/** The result of a pending check: its file is attached first, and then the result may leave its words out (D-57). */
function ResultForm({ check }: { check: Check }) {
  const [text, setText] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const record = useAction(async ({ words, withFile }: { words: string; withFile: File | null }) => {
    if (withFile) {
      await api.upload(`/api/checks/${check.id}/attachments`, withFile);
    }
    return api.post(`/api/checks/${check.id}/result`, { text: words.trim() ? words : null });
  }, { refusals: RESULT_REFUSALS, done: 'Resultado cargado.', invalidates: ['record'] });
  return (
    <form onSubmit={(event) => {
      event.preventDefault();
      if (!text.trim() && !file) {
        setProblem('Escribí el resultado o adjuntá el archivo que lo trae.');
        return;
      }
      setProblem(null);
      record.run({ words: text, withFile: file });
    }}>
      <Stack gap={6}>
        <Textarea label="Resultado" value={text} onChange={(event) => setText(event.currentTarget.value)}
          rows={2} resize="vertical" maxLength={10000} />
        <FileInput label="Archivo del resultado (PDF, PNG o JPG, hasta 10 MB; opcional si escribís el resultado)"
          value={file} onChange={setFile} accept={ACCEPTED_FILES} clearable />
        <Group><Button type="submit" size="xs" loading={record.pending}>Cargar resultado</Button></Group>
        <Said error={problem ?? record.error} notice={record.notice} />
      </Stack>
    </form>
  );
}

/** One order, where it stands, and — for its author, while it is open — cancelling it with a reason (D-55). */
export function OrderLine({ order, checks, practitionerId }: {
  order: Order; checks: Check[]; practitionerId: string | null;
}) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const cancel = useAction((why: string) => api.post(`/api/orders/${order.id}/cancel`, { reason: why }), {
    refusals: CANCEL_ORDER_REFUSALS, done: 'Orden anulada.', invalidates: ['record'],
  });
  return (
    <Stack gap={6} className="record-order">
      <Text fz={14}>
        <strong>Orden:</strong> <span className="notes">{order.description}</span>
        <Text component="span" c="dimmed" fz={13}> — {orderState(order, checks)}</Text>
      </Text>
      {mayCancel(order, practitionerId) && (open ? (
        <form onSubmit={(event) => {
          event.preventDefault();
          if (!reason.trim()) {
            setProblem('Escribí el motivo para anular la orden.');
            return;
          }
          setProblem(null);
          cancel.run(reason);
        }}>
          <Stack gap={6}>
            <Textarea label="Motivo para anularla" value={reason} onChange={(event) => setReason(event.currentTarget.value)}
              rows={1} resize="vertical" maxLength={500} />
            <Group gap={8}>
              <Button type="submit" color="red" size="xs" loading={cancel.pending}>Anular orden</Button>
              <Button variant="subtle" size="xs" onClick={() => setOpen(false)}>Cerrar</Button>
            </Group>
          </Stack>
        </form>
      ) : <Group><Button variant="subtle" color="red" size="xs" px={0} onClick={() => setOpen(true)}>Anular</Button></Group>)}
      <Said error={problem ?? cancel.error} notice={cancel.notice} />
    </Stack>
  );
}

/** Choosing one of the patient's open orders, which a check done now may fulfil (D-55). */
export function OrderChoice({ orders, value, onChange }: {
  orders: Order[]; value: string; onChange: (orderId: string) => void;
}) {
  if (orders.length === 0) {
    return null;
  }
  return (
    <Select label="Cumple la orden (sin descripción, es lo que la orden pide)" value={value || null}
      onChange={(chosen) => onChange(chosen ?? '')} clearable placeholder="—"
      data={orders.map((order) => ({ value: order.id, label: order.description }))} />
  );
}
