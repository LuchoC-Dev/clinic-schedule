import { Anchor, Button, Checkbox, Group, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';

import { type DocumentInput, DocumentFields } from '../components/fields';
import { SearchBox, usePatientSearch } from '../components/PatientSearch';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import { documentOf, type Patient } from '../types';
import { IDENTITY, PATIENT_REFUSALS } from '../words';
import { DetailsFields, detailsBody, type Details, NO_DETAILS } from './details';

/**
 * The patients (D-26, D-60, D-73): found by name, document or alias; registered with what is known of them, the
 * document seen or not; and one who gives no name registered under an alias, to be completed later.
 */
export function PatientsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const query = params.get('query') ?? '';
  const found = usePatientSearch(query);
  const [fullName, setFullName] = useState('');
  const [document, setDocument] = useState<DocumentInput>({ type: '', number: '' });
  const [seen, setSeen] = useState(false);
  const [details, setDetails] = useState<Details>(NO_DETAILS);

  const register = useAction(() => api.post<Patient>('/api/patients', {
    fullName: fullName.trim(), documentType: document.type || null, documentNumber: document.number.trim() || null,
    documentSeen: seen, ...detailsBody(details),
  }), { refusals: PATIENT_REFUSALS, invalidates: ['patients'], onDone: (patient) => navigate(`/patients/${patient.id}`,
    { state: { notice: `Paciente registrado: ${patient.fullName}.` } }) });
  const unidentified = useAction(() => api.post<Patient>('/api/patients/unidentified'), {
    refusals: PATIENT_REFUSALS, invalidates: ['patients'],
    onDone: (patient) => navigate(`/patients/${patient.id}`,
      { state: { notice: `Paciente registrado sin datos: ${patient.alias}.` } }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    register.run(undefined);
  }

  return (
    <Stack gap={20}>
      <Title order={1} fz={28}>Pacientes</Title>
      <SearchBox label="Buscar" initial={query} onSearch={(text) => setParams(text ? { query: text } : {})} />
      {query && found.data?.length === 0 && <Text c="dimmed">No hay pacientes que coincidan.</Text>}
      {found.data && found.data.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead>
              <Table.Tr><Table.Th>Nombre</Table.Th><Table.Th>Documento</Table.Th><Table.Th>Identidad</Table.Th>
                <Table.Th /></Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {found.data.map((patient) => (
                <Table.Tr key={patient.id}>
                  <Table.Td>
                    {patient.fullName}
                    {patient.alias && patient.alias !== patient.fullName && <Text span c="dimmed"> ({patient.alias})</Text>}
                  </Table.Td>
                  <Table.Td>{documentOf(patient) ?? '—'}</Table.Td>
                  <Table.Td c={patient.identity === 'VERIFIED' ? undefined : 'dimmed'}>{IDENTITY[patient.identity]}</Table.Td>
                  <Table.Td>
                    <Group gap={12}>
                      <Anchor component={Link} to={`/patients/${patient.id}`}>Datos</Anchor>
                      <Anchor component={Link} to={`/patients/${patient.id}/appointments`}>Ver turnos</Anchor>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}

      <Title order={2} fz={20}>Registrar paciente</Title>
      <Paper className="card" p={20} maw={880}>
        <form onSubmit={submit}>
          <Stack>
            <TextInput label="Nombre completo" required maxLength={150} value={fullName}
              onChange={(event) => setFullName(event.currentTarget.value)} />
            <DocumentFields value={document} onChange={setDocument} />
            <Checkbox label="Vi el documento" checked={seen} onChange={(event) => setSeen(event.currentTarget.checked)} />
            <DetailsFields value={details} onChange={setDetails} />
            <Said error={register.error} />
            <Group><Button type="submit" loading={register.pending}>Registrar</Button></Group>
          </Stack>
        </form>
      </Paper>
      {/* Nobody is turned away for lack of a name (D-60). */}
      <Paper className="card" p={20} maw={880}>
        <Group justify="space-between">
          <Text c="dimmed" fz={14}>Si el paciente no da su nombre, se lo registra con un alias y se completa después.</Text>
          <Button variant="default" loading={unidentified.pending} onClick={() => unidentified.run(undefined)}>
            Registrar sin datos (NN)
          </Button>
        </Group>
        <Said error={unidentified.error} />
      </Paper>
    </Stack>
  );
}
