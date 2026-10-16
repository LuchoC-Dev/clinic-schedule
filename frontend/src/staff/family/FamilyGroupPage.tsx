import { Alert, Anchor, Button, Checkbox, Group, Loader, NativeSelect, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';

import { when } from '../../shared/dates';
import { describeError } from '../../shared/errors';
import { SearchBox, usePatientSearch } from '../components/PatientSearch';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { documentOf, type Patient } from '../types';
import { CHILD_RELATIONS, FAMILY_CHANGES, FAMILY_REFUSALS, who } from '../words';
import { type FamilyChange, type FamilyGroup, type Member, memberView, namesOf } from './family';

/**
 * A family group (D-84, D-92): its members, each with their age, whether the group sees their appointments and who
 * asked for a child; proving a child's relation, a member of 13 or older sharing or not, and taking one out; adding
 * someone of 13 or older, present and agreeing, or a child under 13 at an adult's request, with the documents seen;
 * and its history.
 */
export function FamilyGroupPage() {
  const { id } = useParams();
  const location = useLocation();
  const created = (location.state as { notice?: string } | null)?.notice ?? null;
  const group = useQuery({ queryKey: ['family-group', id], queryFn: () => api.get<FamilyGroup>(`/api/family-groups/${id}`) });
  const history = useQuery({ queryKey: ['family-group', id, 'changes'],
    queryFn: () => api.get<FamilyChange[]>(`/api/family-groups/${id}/changes`) });
  const [query, setQuery] = useState('');
  const found = usePatientSearch(query);
  const act = useAction(({ path, body, done }: { path: string; body: unknown; done: string }) =>
    api.post(`/api/family-groups/${id}/${path}`, body).then(() => done),
  { refusals: FAMILY_REFUSALS, invalidates: ['family-group', 'family-groups'], done: (done) => done });

  if (group.isPending) {
    return <Loader aria-label="Cargando el grupo" />;
  }
  if (group.isError) {
    return <Alert color="red" role="alert">{describeError(group.error, FAMILY_REFUSALS)}</Alert>;
  }
  const members = group.data.members;
  const names = namesOf(members, history.data ?? []);
  const adults = members.filter((member) => memberView(member, names).adult);

  return (
    <Stack gap={20}>
      <Title order={1} fz={28}>{group.data.name}</Title>
      <Said error={act.error} notice={act.notice ?? created} />

      <Title order={2} fz={20}>Miembros</Title>
      {members.length === 0 ? <Text c="dimmed">Todavía no tiene miembros.</Text> : (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Paciente</Table.Th><Table.Th>Edad</Table.Th><Table.Th>Turnos</Table.Th>
              <Table.Th /></Table.Tr></Table.Thead>
            <Table.Tbody>
              {members.map((member) => (
                <MemberRow key={member.patientId} member={member} names={names}
                  act={(path, body, done) => act.run({ path, body, done })} />
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}

      <Title order={2} fz={20}>Agregar</Title>
      <SearchBox onSearch={setQuery} />
      {query && found.data?.length === 0 && <Text c="dimmed">Ningún paciente coincide.</Text>}
      {found.data && found.data.length > 0 && (
        <section className="card">
          <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
            <Table.Thead><Table.Tr><Table.Th>Paciente</Table.Th><Table.Th>Entra por sí mismo (13 o más)</Table.Th>
              <Table.Th>Menor de 13</Table.Th></Table.Tr></Table.Thead>
            <Table.Tbody>
              {found.data.map((patient) => (
                <FoundRow key={patient.id} patient={patient} adults={adults}
                  act={(path, body, done) => act.run({ path, body, done })} />
              ))}
            </Table.Tbody>
          </Table>
        </section>
      )}
      <Text c="dimmed" fz={14}>Un adulto que entra comparte sus turnos con el grupo. De 13 a 17, el paciente elige; un menor
        de 13 los comparte siempre.</Text>

      <Title order={2} fz={20}>Historial</Title>
      <section className="card">
        <Table className="card-table" verticalSpacing={10} horizontalSpacing={14}>
          <Table.Thead><Table.Tr><Table.Th>Cuándo</Table.Th><Table.Th>Qué</Table.Th><Table.Th>Quién</Table.Th></Table.Tr></Table.Thead>
          <Table.Tbody>
            {(history.data ?? []).map((change, index) => (
              <Table.Tr key={index}>
                <Table.Td>{when(change.at)}</Table.Td>
                <Table.Td>{FAMILY_CHANGES[change.action] ?? change.action}{change.patient ? ` — ${change.patient}` : ''}</Table.Td>
                <Table.Td>{who(change.by)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </section>
      <Anchor component={Link} to="/family-groups">Volver a los grupos</Anchor>
    </Stack>
  );
}

type Act = (path: string, body: unknown, done: string) => void;

function MemberRow({ member, names, act }: { member: Member; names: Record<string, string>; act: Act }) {
  const view = memberView(member, names);
  const [proofSeen, setProofSeen] = useState(false);
  const path = `members/${member.patientId}`;
  return (
    <Table.Tr>
      <Table.Td>
        <Anchor component={Link} to={`/patients/${member.patientId}`}>{member.fullName}</Anchor>
        {view.requestedBy && <Text fz={13} c="dimmed">{view.requestedBy}</Text>}
        {!member.relationProved && <Text fz={13} c="red.8">Vínculo sin probar · pedido desde la web</Text>}
      </Table.Td>
      <Table.Td>{view.age}</Table.Td>
      <Table.Td>{view.sharing}</Table.Td>
      <Table.Td>
        <Group gap={6} justify="flex-end">
          {!member.relationProved && (
            <>
              <Checkbox label="Vi lo que prueba el vínculo" checked={proofSeen}
                onChange={(event) => setProofSeen(event.currentTarget.checked)} />
              <Button size="xs" variant="default" disabled={!proofSeen}
                onClick={() => act(`${path}/relation-proof`, { proofSeen }, 'Vínculo probado.')}>Probar vínculo</Button>
            </>
          )}
          {view.decides && (
            <Button size="xs" variant="default" onClick={() => act(`${path}/sharing`, { shares: !member.shares },
              member.shares ? 'Sus turnos ya no se comparten con el grupo.' : 'Ahora comparte sus turnos con el grupo.')}>
              {member.shares ? 'Dejar de compartir' : 'Compartir'}
            </Button>
          )}
          <Button size="xs" color="red" variant="light" onClick={() => act(`${path}/remove`, {}, 'Salió del grupo.')}>
            Sacar del grupo
          </Button>
        </Group>
      </Table.Td>
    </Table.Tr>
  );
}

function FoundRow({ patient, adults, act }: { patient: Patient; adults: Member[]; act: Act }) {
  const [shares, setShares] = useState(false);
  const [requestedBy, setRequestedBy] = useState(adults[0]?.patientId ?? '');
  const [relation, setRelation] = useState('MOTHER');
  const [proofSeen, setProofSeen] = useState(false);
  return (
    <Table.Tr>
      <Table.Td>
        {patient.fullName}
        <Text fz={13} c="dimmed">{documentOf(patient) ?? 'Sin documento'}</Text>
      </Table.Td>
      <Table.Td>
        <Group gap={6}>
          <Checkbox label="De 13 a 17: elige compartir" checked={shares}
            onChange={(event) => setShares(event.currentTarget.checked)} />
          <Button size="xs" onClick={() => act('members', { patientId: patient.id, shares }, 'Entró al grupo.')}>
            Entra al grupo
          </Button>
        </Group>
      </Table.Td>
      <Table.Td>
        {adults.length === 0 ? <Text c="dimmed" fz={14}>Primero tiene que entrar un adulto.</Text> : (
          <Group gap={6} align="flex-end">
            <NativeSelect size="xs" label="A pedido de" value={requestedBy}
              onChange={(event) => setRequestedBy(event.currentTarget.value)}
              data={adults.map((adult) => ({ value: adult.patientId, label: adult.fullName }))} />
            <NativeSelect size="xs" label="que es su" value={relation} onChange={(event) => setRelation(event.currentTarget.value)}
              data={Object.entries(CHILD_RELATIONS).map(([value, label]) => ({ value, label }))} />
            <Checkbox label="Vi la documentación" checked={proofSeen}
              onChange={(event) => setProofSeen(event.currentTarget.checked)} />
            <Button size="xs" disabled={!proofSeen}
              onClick={() => act('children', { patientId: patient.id, requestedBy, relation, proofSeen }, 'Menor agregado al grupo.')}>
              Agregar menor
            </Button>
          </Group>
        )}
      </Table.Td>
    </Table.Tr>
  );
}
