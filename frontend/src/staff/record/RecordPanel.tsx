import { Alert, Group, Loader, Stack, Table, Tabs, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { shortDay, todayAtThePractice, when } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { api, useUser } from '../session';
import { RECORD_REFUSALS } from './consultation';
import { ConsultationForm, LooseCheckForm } from './ConsultationForm';
import { Addenda, AttachButton, CheckBlock, FileLink, FileList, OrderLine } from './parts';
import {
  type ClinicalRecord, type Consultation, allChecks, allOrders, filesOf, grantedFor, historyOf, nameOf, openOrders,
  patientLine,
} from './record';
import './record.css';

/**
 * A patient's clinical record, for a practitioner (D-104): who the patient is, then its history — the new
 * consultation on top when the patient arrived to be seen today —, its checks, its orders, its files and who was
 * granted access to it, every practitioner's, the newest first.
 *
 * Every reading is recorded by the server (D-59), so the record is read when the screen opens it and after each
 * thing written to it, never again on its own when the window regains focus; and nothing of it stays in memory once
 * the screen is left (D-97).
 */
export function RecordPanel({ patientId, onTouched }: { patientId: string; onTouched?: (touched: boolean) => void }) {
  const record = useQuery({
    queryKey: ['record', patientId],
    queryFn: () => api.get<ClinicalRecord>(`/api/clinical-records/${patientId}`),
    gcTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  if (record.isPending) {
    return <Loader aria-label="Cargando la historia clínica" />;
  }
  if (record.isError) {
    return <Alert color="red" role="alert">{describeError(record.error, RECORD_REFUSALS)}</Alert>;
  }
  return <Record patientId={patientId} record={record.data} onTouched={onTouched} />;
}

function Record({ patientId, record, onTouched }: {
  patientId: string; record: ClinicalRecord; onTouched?: (touched: boolean) => void;
}) {
  const { practitionerId } = useUser();
  const [tab, setTab] = useState<string | null>('history');
  const checks = allChecks(record);
  const orders = allOrders(record);
  const open = openOrders(record);
  const history = historyOf(record);
  return (
    <section className="card record" aria-label="Historia clínica">
      <Group justify="space-between" align="flex-start" gap={12}>
        <Stack gap={4}>
          <Title order={2} fz={22}>{record.patient.fullName ?? '(paciente desconocido)'}</Title>
          <Text fz={14} c="dimmed">{patientLine(record.patient, todayAtThePractice(new Date()))}</Text>
        </Stack>
        <AttachButton path={`/api/clinical-records/${patientId}/attachments`} label="Subir archivo a la historia"
          done="Archivo subido a la historia." />
      </Group>

      <Tabs value={tab} onChange={setTab}>
        <Tabs.List>
          <Tabs.Tab value="history">Historia</Tabs.Tab>
          <Tabs.Tab value="checks">Estudios</Tabs.Tab>
          <Tabs.Tab value="orders">Órdenes</Tabs.Tab>
          <Tabs.Tab value="files">Archivos</Tabs.Tab>
          {record.accessGrants.length > 0 && <Tabs.Tab value="grants">Accesos</Tabs.Tab>}
        </Tabs.List>

        <Tabs.Panel value="history" pt={16} keepMounted>
          <Stack gap={16}>
            {record.mayRecord && <ConsultationForm patientId={patientId} openOrders={open} onTouched={onTouched} />}
            {history.length === 0 && <Text c="dimmed">Todavía no hay nada registrado.</Text>}
            <ol className="record-history">
              {history.map((entry) => (
                <li key={`${entry.kind}-${entry.kind === 'consultation' ? entry.consultation.id
                  : entry.kind === 'check' ? entry.check.id : entry.file.id}`}>
                  <span className="mono record-when">{shortDay(entry.at)}</span>
                  <div className="record-entry">
                    {entry.kind === 'consultation' && (
                      <ConsultationEntry consultation={entry.consultation} record={record} practitionerId={practitionerId} />
                    )}
                    {entry.kind === 'check' && <CheckBlock check={entry.check} record={record} />}
                    {entry.kind === 'file' && (
                      <>
                        <Text fw={600}>Archivo de la historia · {nameOf(entry.file.uploadedByName)}</Text>
                        <FileLink file={entry.file} />
                      </>
                    )}
                  </div>
                </li>
              ))}
            </ol>
          </Stack>
        </Tabs.Panel>

        <Tabs.Panel value="checks" pt={16}>
          <Stack gap={16}>
            {record.mayRecord && <LooseCheckForm patientId={patientId} openOrders={open} />}
            {checks.length === 0 && <Text c="dimmed">No hay estudios.</Text>}
            {checks.map((check) => <CheckBlock key={check.id} check={check} record={record} />)}
          </Stack>
        </Tabs.Panel>

        <Tabs.Panel value="orders" pt={16}>
          <Stack gap={12}>
            {orders.length === 0 && <Text c="dimmed">No hay órdenes.</Text>}
            {orders.map((order) => (
              <div key={order.id}>
                <Text fz={13} c="dimmed">{when(order.orderedAt)} — {nameOf(order.orderedByName)}</Text>
                <OrderLine order={order} checks={checks} practitionerId={practitionerId} />
              </div>
            ))}
          </Stack>
        </Tabs.Panel>

        <Tabs.Panel value="files" pt={16}>
          <Stack gap={12}>
            <Title order={3} fz={16}>Archivos de la historia</Title>
            {filesOf(record, 'RECORD').length === 0 && <Text c="dimmed">No hay archivos de la historia.</Text>}
            <FileList files={filesOf(record, 'RECORD')} />
            <Text fz={13} c="dimmed">
              Una historia en papel escaneada con su sello, un informe que nunca se subió: PDF, PNG o JPG, hasta 10 MB.
            </Text>
            <Title order={3} fz={16}>De las consultas y los estudios</Title>
            {record.attachments.filter((file) => file.owner !== 'RECORD').length === 0
              && <Text c="dimmed">No hay archivos adjuntos a consultas ni a estudios.</Text>}
            <FileList files={record.attachments.filter((file) => file.owner !== 'RECORD')
              .sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt))} />
          </Stack>
        </Tabs.Panel>

        {record.accessGrants.length > 0 && (
          <Tabs.Panel value="grants" pt={16}>
            <Table className="card-table">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Profesional</Table.Th><Table.Th>Cuándo</Table.Th><Table.Th>Hasta</Table.Th>
                  <Table.Th>Para</Table.Th><Table.Th>Motivo</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {record.accessGrants.map((grant) => (
                  <Table.Tr key={grant.id}>
                    <Table.Td>{nameOf(grant.practitionerName)}</Table.Td>
                    <Table.Td>{when(grant.grantedAt)}</Table.Td>
                    <Table.Td>{shortDay(grant.validUntil)}</Table.Td>
                    <Table.Td className="notes">{grantedFor(grant, checks)}</Table.Td>
                    <Table.Td className="notes">{grant.reason ?? ''}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Text fz={13} c="dimmed" mt={8}>Aprobados automáticamente.</Text>
          </Tabs.Panel>
        )}
      </Tabs>
      <Text fz={13} c="dimmed">Cada lectura de esta historia queda registrada.</Text>
    </section>
  );
}

/** A consultation: what was consulted, its prescriptions, checks, orders, files and addenda (D-47, D-52, D-55, D-57). */
function ConsultationEntry({ consultation, record, practitionerId }: {
  consultation: Consultation; record: ClinicalRecord; practitionerId: string | null;
}) {
  const checks = allChecks(record);
  return (
    <Stack gap={8}>
      <Text fw={600}>Consulta · {nameOf(consultation.practitionerName)}
        <Text component="span" c="dimmed" fz={13} fw={400}> · {when(consultation.recordedAt)}</Text>
      </Text>
      <Text fz={15} className="notes">{consultation.notes}</Text>
      {consultation.items.map((item, index) => (
        <Text key={index} fz={14}><strong>Receta:</strong> <span className="notes">{item.description}</span></Text>
      ))}
      {[...consultation.checks].sort((a, b) => a.performedAt.localeCompare(b.performedAt))
        .map((check) => <CheckBlock key={check.id} check={check} record={record} />)}
      {consultation.orders.map((order) => (
        <OrderLine key={order.id} order={order} checks={checks} practitionerId={practitionerId} />
      ))}
      <FileList files={filesOf(record, 'CONSULTATION', consultation.id)} />
      <AttachButton path={`/api/consultations/${consultation.id}/attachments`} />
      <Addenda addenda={consultation.addenda} path={`/api/consultations/${consultation.id}/addenda`} label="Aclaración" />
    </Stack>
  );
}
