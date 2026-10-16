import { Alert, Anchor, Button, Checkbox, Group, Loader, Stack, Table, Text, Textarea, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type ReactNode, useState } from 'react';
import { Link, useParams } from 'react-router';

import { shortDay, timeOf } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { grantedUntil, useReasonLabel } from '../record/access';
import { AttachButton } from '../record/parts';
import { nameOf } from '../record/record';
import { api } from '../session';
import {
  AUTHENTICATION_REFUSALS, COPY_ACCESS_REFUSALS, REASSEMBLY_REFUSALS, type RecordCopy, askedBy, authenticatedNotice,
  deliveryOf, personOf, timeLeft,
} from './copies';

const at = (dateTime: string) => `${shortDay(dateTime)} ${timeOf(dateTime)}`;

/**
 * One copy of a record (D-95, D-106): who asked and how it is delivered, its code, when the record is copied as it
 * stood, and the time left or who authenticated it. A practitioner who may read the record opens its PDF — a reading
 * (D-96) —, uploads what was missing to the record as a whole and puts it together again (D-94), and authenticates it,
 * saying they reviewed it is complete; one who may not asks for access first, as for a pending check (D-54).
 */
export function RecordCopyPage() {
  const { id = '' } = useParams();
  const copy = useQuery({ queryKey: ['record-copy', id], queryFn: () => api.get<RecordCopy>(`/api/record-copies/${id}`) });
  if (copy.isPending) {
    return <Loader aria-label="Cargando la copia" />;
  }
  if (copy.isError) {
    return <Alert color="red" role="alert">{describeError(copy.error)}</Alert>;
  }
  return <Copy copy={copy.data} />;
}

function Copy({ copy }: { copy: RecordCopy }) {
  // Here, so what it says stays once access is granted and the copy, read again, may be reviewed.
  const ask = useAction(
    (why: string) => api.post<{ validUntil: string }>(`/api/record-copies/${copy.id}/access-grants`,
      { reason: why.trim() ? why : null }),
    { refusals: COPY_ACCESS_REFUSALS, invalidates: ['record-copy', 'record-copies'],
      done: (grant) => grantedUntil(grant.validUntil) },
  );
  const authenticated = copy.authentication !== null;
  const onPaper = copy.delivery === 'PAPER';
  return (
    <Stack gap={20}>
      <Anchor component={Link} to="/record-copies" fz={14}>‹ Copias pedidas</Anchor>
      <Title order={1} fz={26}>Copia de la historia de {personOf(copy.patient)}</Title>
      <section className="card">
        <Table className="card-table" verticalSpacing={10} horizontalSpacing={16}>
          <Table.Tbody>
            <Row label="Pidió">{askedBy(copy)}, el {at(copy.requestedAt)}</Row>
            <Row label="Entrega">{deliveryOf(copy)}</Row>
            <Row label="Código de verificación"><span className="mono">{copy.code}</span></Row>
            <Row label="Historia al">{at(copy.assembledAt)}</Row>
            {!authenticated && (
              <Row label="Plazo"><Text span c={copy.late ? 'red' : undefined} fw={copy.late ? 600 : undefined}>
                {timeLeft(copy.minutesLeft)}</Text></Row>
            )}
            {copy.authentication && (
              <Row label="Autenticada">
                {nameOf(copy.authentication.practitionerName)}, {at(copy.authentication.at)}
              </Row>
            )}
            {copy.handedOverAt && <Row label="Entregada en recepción">{at(copy.handedOverAt)}</Row>}
          </Table.Tbody>
        </Table>
      </section>

      {!copy.mayRead && (
        <Stack gap={8}>
          <Text c="dimmed">No tenés acceso a esta historia. Para revisar y autenticar la copia, pedilo.</Text>
          {!authenticated && <AskAccess ask={ask} />}
        </Stack>
      )}
      <Said error={ask.error} notice={ask.notice} />

      {copy.mayRead && (
        <Stack gap={16}>
          <Group gap={8}>
            <Button component="a" href={`/api/record-copies/${copy.id}/pdf`} target="_blank" rel="noopener"
              variant={authenticated ? 'default' : 'filled'}>
              {authenticated && onPaper ? 'Ver e imprimir la copia' : 'Ver la copia'}
            </Button>
            <Button component={Link} to={`/clinical-records/${copy.patient.id}`} variant="default">Ver historia</Button>
          </Group>
          {authenticated && onPaper && (
            <Text c="dimmed">
              Imprimila, firmala y sellala, y dejala en recepción en un sobre cerrado: el paciente la retira con su
              documento.
            </Text>
          )}
          {!authenticated && <Review copy={copy} />}
        </Stack>
      )}
    </Stack>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Table.Tr>
      <Table.Th style={{ width: 220 }}>{label}</Table.Th>
      <Table.Td>{children}</Table.Td>
    </Table.Tr>
  );
}

/** What was missing, uploaded and put together again; then the authentication, of the assembly reviewed (D-95). */
function Review({ copy }: { copy: RecordCopy }) {
  const [reviewed, setReviewed] = useState(false);
  const reassemble = useAction(() => api.post<RecordCopy>(`/api/record-copies/${copy.id}/reassembly`), {
    refusals: REASSEMBLY_REFUSALS, invalidates: ['record-copy', 'record-copies'],
    done: 'Copia armada de nuevo con la historia de ahora. Revisala antes de autenticarla.',
  });
  const authenticate = useAction(
    (body: { assembly: number; reviewed: boolean }) =>
      api.post<RecordCopy>(`/api/record-copies/${copy.id}/authentication`, body),
    { refusals: AUTHENTICATION_REFUSALS, invalidates: ['record-copy', 'record-copies'], done: authenticatedNotice },
  );
  return (
    <Stack gap={16}>
      <Title order={2} fz={20}>Si falta algo</Title>
      <AttachButton path={`/api/clinical-records/${copy.patient.id}/attachments`} label="Subir a la historia"
        done="Archivo subido a la historia. Volvé a armar la copia para que lo incluya." />
      <Group>
        <Button variant="default" loading={reassemble.pending} onClick={() => reassemble.run(undefined)}>
          Volver a armar la copia
        </Button>
      </Group>
      <Said error={reassemble.error} notice={reassemble.notice} />

      <Title order={2} fz={20}>Autenticar</Title>
      <form onSubmit={(event) => {
        event.preventDefault();
        authenticate.run({ assembly: copy.assembly, reviewed });
      }}>
        <Stack gap={10}>
          <Checkbox label="Revisé la copia y está completa" checked={reviewed}
            onChange={(event) => setReviewed(event.currentTarget.checked)} />
          <Text fz={13} c="dimmed">
            Tu ingreso a la aplicación es tu firma electrónica (Ley 25.506, art. 5; Decreto 1089/2012, art. 13).
          </Text>
          <Group><Button type="submit" loading={authenticate.pending}>Autenticar copia</Button></Group>
          <Said error={authenticate.error} notice={authenticate.notice} />
        </Stack>
      </form>
    </Stack>
  );
}

/** Access to the record, to authenticate its copy: granted at once and recorded, as for a check (D-54, D-95). */
function AskAccess({ ask }: { ask: { run: (reason: string) => void; pending: boolean } }) {
  const label = useReasonLabel();
  const [reason, setReason] = useState('');
  return (
    <form onSubmit={(event) => { event.preventDefault(); ask.run(reason); }}>
      <Stack gap={8} maw={520}>
        <Textarea label={label} value={reason} onChange={(event) => setReason(event.currentTarget.value)}
          rows={2} maxLength={500} />
        <Group><Button type="submit" variant="default" loading={ask.pending}>Pedir acceso</Button></Group>
      </Stack>
    </form>
  );
}
