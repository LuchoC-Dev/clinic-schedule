import { Alert, Anchor, Button, Checkbox, Group, Loader, NativeSelect, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';

import { when } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { type DocumentInput, DocumentFields } from '../components/fields';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import type { Patient } from '../types';
import { DETAIL_FIELDS, IDENTITY, PATIENT_REFUSALS, who } from '../words';
import { DetailsFields, detailsBody, detailsOf, type Details } from './details';
import { consentChoices, type DetailsChange, detailText, type IdentityChange, identityText } from './patient';

/**
 * A patient's screen (D-26, D-60, D-73, D-74, D-77): their identity, corrected and verified, with its history;
 * their details, each phone and email saying whose it is, with their history; and who agreed to be reminded on
 * each channel.
 */
export function PatientPage() {
  const { id } = useParams();
  const patient = useQuery({ queryKey: ['patient', id], queryFn: () => api.get<Patient>(`/api/patients/${id}`) });
  if (patient.isPending) {
    return <Loader aria-label="Cargando el paciente" />;
  }
  if (patient.isError) {
    return <Alert color="red" role="alert">{describeError(patient.error, PATIENT_REFUSALS)}</Alert>;
  }
  return <PatientScreen key={patient.data.id} patient={patient.data} />;
}

function PatientScreen({ patient }: { patient: Patient }) {
  const location = useLocation();
  const registered = (location.state as { notice?: string } | null)?.notice ?? null;
  const identityChanges = useQuery({ queryKey: ['patient', patient.id, 'identity-changes'],
    queryFn: () => api.get<IdentityChange[]>(`/api/patients/${patient.id}/identity-changes`) });
  const detailsChanges = useQuery({ queryKey: ['patient', patient.id, 'details-changes'],
    queryFn: () => api.get<DetailsChange[]>(`/api/patients/${patient.id}/details-changes`) });

  const [fullName, setFullName] = useState(patient.fullName);
  const [document, setDocument] = useState<DocumentInput>(
    { type: patient.document?.type ?? '', number: patient.document?.number ?? '' });
  const [seen, setSeen] = useState(false);
  const [details, setDetails] = useState<Details>(detailsOf(patient));
  const [phoneConsent, setPhoneConsent] = useState(patient.phone?.consent ?? '');
  const [emailConsent, setEmailConsent] = useState(patient.email?.consent ?? '');

  const saveIdentity = useAction(() => api.put(`/api/patients/${patient.id}`, {
    fullName: fullName.trim(), documentType: document.type || null, documentNumber: document.number.trim() || null,
    documentSeen: seen,
  }), { refusals: PATIENT_REFUSALS, done: 'Datos guardados.', invalidates: ['patient', 'patients'] });
  const saveDetails = useAction(() => api.put(`/api/patients/${patient.id}/details`, detailsBody(details)),
    { refusals: PATIENT_REFUSALS, done: 'Datos personales guardados.', invalidates: ['patient'] });
  const saveConsent = useAction(() => api.put(`/api/patients/${patient.id}/contact-consent`, {
    phone: phoneConsent || null, email: emailConsent || null,
  }), {
    refusals: () => 'El consentimiento va con un teléfono o un email cargado, y solo lo da la persona responsable si '
      + 'el teléfono o el email son de ella.',
    done: 'Consentimiento guardado.', invalidates: ['patient'],
  });
  const send = (action: { run: (input: undefined) => void }) => (event: FormEvent) => {
    event.preventDefault();
    action.run(undefined);
  };
  const phoneChoices = consentChoices(patient.phone);
  const emailChoices = consentChoices(patient.email);

  return (
    <Stack gap={20} maw={960}>
      <Stack gap={4}>
        <Title order={1} fz={28}>{patient.fullName}</Title>
        <Text c="dimmed" fz={15}>
          Identidad: {IDENTITY[patient.identity]}
          {patient.alias && ` · alias ${patient.alias}`}
          {patient.registeredVia === 'WEB' && ' · Registrado desde la web'}
          {' · '}<Anchor component={Link} to={`/patients/${patient.id}/appointments`}>Turnos</Anchor>
          {' · '}<Anchor href={`/patient-accounts?patient=${patient.id}`}>Cuenta web</Anchor>
          {' · '}<Anchor href={`/family-groups?patient=${patient.id}`}>Grupos familiares</Anchor>
        </Text>
      </Stack>
      <Said notice={registered} />

      <Title order={2} fz={20}>Datos</Title>
      <Paper className="card" p={20}>
        <form onSubmit={send(saveIdentity)}>
          <Stack>
            <TextInput label="Nombre completo" required maxLength={150} value={fullName}
              onChange={(event) => setFullName(event.currentTarget.value)} />
            <DocumentFields value={document} onChange={setDocument} />
            <Checkbox label="Vi el documento" checked={seen} onChange={(event) => setSeen(event.currentTarget.checked)} />
            <Text c="dimmed" fz={14}>Cambiar el nombre o el documento de una identidad verificada la deja sin verificar,
              salvo que marques que viste el documento.</Text>
            <Said error={saveIdentity.error} notice={saveIdentity.notice} />
            <Group><Button type="submit" loading={saveIdentity.pending}>Guardar</Button></Group>
          </Stack>
        </form>
      </Paper>

      <Title order={2} fz={20}>Cambios de identidad</Title>
      <History empty={identityChanges.data?.length === 0} heads={['Cuándo', 'Quién', 'Antes', 'Después']}
        rows={(identityChanges.data ?? []).map((change) => [when(change.changedAt), who(change.changedBy),
          identityText(change.before), identityText(change.after)])} />

      <Title order={2} fz={20}>Datos personales</Title>
      <Paper className="card" p={20}>
        <form onSubmit={send(saveDetails)}>
          <Stack>
            <DetailsFields value={details} onChange={setDetails} />
            <Said error={saveDetails.error} notice={saveDetails.notice} />
            <Group><Button type="submit" loading={saveDetails.pending}>Guardar</Button></Group>
          </Stack>
        </form>
      </Paper>

      {/* Who agreed to be reminded on each channel (D-77): a new number, address or owner drops it. */}
      <Title order={2} fz={20}>Recordatorios</Title>
      {phoneChoices.length === 0 && emailChoices.length === 0 ? (
        <Text c="dimmed">Sin teléfono ni email cargados: no hay a dónde mandarle recordatorios.</Text>
      ) : (
        <Paper className="card" p={20}>
          <form onSubmit={send(saveConsent)}>
            <Stack>
              <Group align="flex-end">
                {phoneChoices.length > 0 && (
                  <NativeSelect label="Al teléfono" value={phoneConsent} data={phoneChoices}
                    onChange={(event) => setPhoneConsent(event.currentTarget.value)} />
                )}
                {emailChoices.length > 0 && (
                  <NativeSelect label="Al email" value={emailConsent} data={emailChoices}
                    onChange={(event) => setEmailConsent(event.currentTarget.value)} />
                )}
              </Group>
              <Said error={saveConsent.error} notice={saveConsent.notice} />
              <Group><Button type="submit" loading={saveConsent.pending}>Guardar</Button></Group>
            </Stack>
          </form>
        </Paper>
      )}

      <Title order={2} fz={20}>Cambios de datos personales</Title>
      <History empty={detailsChanges.data?.length === 0} heads={['Cuándo', 'Quién', 'Dato', 'Antes', 'Después']}
        rows={(detailsChanges.data ?? []).map((change) => [when(change.changedAt), who(change.changedBy),
          DETAIL_FIELDS[change.field] ?? change.field, detailText(change.field, change.before),
          detailText(change.field, change.after)])} />
      <Anchor component={Link} to="/patients">Volver a pacientes</Anchor>
    </Stack>
  );
}

function History({ empty, heads, rows }: { empty: boolean; heads: string[]; rows: string[][] }) {
  if (empty) {
    return <Text c="dimmed">Sin cambios desde que se registró.</Text>;
  }
  return (
    <section className="card">
      <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
        <Table.Thead><Table.Tr>{heads.map((head) => <Table.Th key={head}>{head}</Table.Th>)}</Table.Tr></Table.Thead>
        <Table.Tbody>
          {rows.map((cells, index) => (
            <Table.Tr key={index}>{cells.map((cell, column) => <Table.Td key={column}>{cell}</Table.Td>)}</Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </section>
  );
}
