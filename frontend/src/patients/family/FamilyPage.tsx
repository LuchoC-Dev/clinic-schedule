import { Anchor, Button, Group as Row, Loader, NativeSelect, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useContext, useState } from 'react';
import { Link } from 'react-router';

import { describeError } from '../../shared/errors';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import type { Profile } from '../profile/profile';
import { RELATIONS } from '../register/register';
import { api, SessionContext } from '../session';
import { CHILD_REFUSALS, childAsked, GONE, type Group, memberRow, sharesMine } from './family';

/**
 * *Mi familia* (D-85, D-92): the patient's groups, who is in each — their name, how old as the group counts it and
 * whether it sees their appointments —, the patient's own choices to share or leave, and, for an adult, registering a
 * child under 13.
 */
export function FamilyPage() {
  const { documentTypes } = useContext(SessionContext);
  const groups = useQuery({ queryKey: ['family'], queryFn: () => api.get<Group[]>('/api/portal/family') });
  const profile = useQuery({ queryKey: ['profile'], queryFn: () => api.get<Profile>('/api/portal/profile') });
  const emptyChild = { fullName: '', documentType: documentTypes[0] ?? 'DNI', documentNumber: '', birthDate: '',
    relation: 'MOTHER', groupId: null as string | null };
  const [child, setChild] = useState(emptyChild);
  const share = useAction(async ({ id, shares }: { id: string; shares: boolean }) => {
    await api.post(`/api/portal/family/${id}/sharing`, { shares });
    return shares;
  }, {
    refusals: () => GONE, invalidates: ['family'],
    done: (shares) => (shares ? 'Listo: tu familia ahora ve tus turnos.' : 'Listo: tus turnos ya no los ve tu familia.'),
  });
  const leave = useAction((id: string) => api.post(`/api/portal/family/${id}/leave`), {
    refusals: () => GONE, invalidates: ['family'], done: 'Saliste del grupo.',
  });
  // The first group is chosen until the patient chooses, as the Thymeleaf form offered it.
  const groupId = child.groupId ?? groups.data?.[0]?.id ?? '';
  const register = useAction(() => api.post('/api/portal/family/children', {
    ...child, birthDate: child.birthDate === '' ? null : child.birthDate, groupId: groupId || null,
  }), {
    refusals: CHILD_REFUSALS, invalidates: ['family'], done: childAsked(child.fullName),
    onDone: () => setChild(emptyChild),
  });
  const set = (key: keyof typeof child) => (event: { currentTarget: { value: string } }) => {
    const { value } = event.currentTarget;
    setChild((before) => ({ ...before, [key]: value }));
  };

  function submit(event: FormEvent) {
    event.preventDefault();
    register.run(undefined);
  }

  if (groups.isError) {
    return <Said error={describeError(groups.error)} />;
  }
  if (!groups.data) {
    return <Loader size="sm" aria-label="Buscando tu familia" />;
  }
  const said = share.error ?? leave.error ?? null;
  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>Mi familia</Title>
      <Said error={said} notice={said ? null : share.notice || leave.notice} />
      {groups.data.length === 0 && (
        <Text c="dimmed">No estás en ningún grupo familiar. Los arma recepción: acercate con tu documento, y con el de
          quien quieras sumar. Si registrás a un hijo desde acá, se forma uno con él.</Text>
      )}
      {groups.data.map((group) => {
        const mine = sharesMine(group);
        return (
          <Paper key={group.id} className="card" p={16} component="section" aria-label={group.name}>
            <Stack gap={12}>
              <Title order={2} fz={20}>{group.name}</Title>
              <Table verticalSpacing={8}>
                <Table.Thead><Table.Tr><Table.Th>Miembro</Table.Th><Table.Th>Edad</Table.Th>
                  <Table.Th>Turnos</Table.Th></Table.Tr></Table.Thead>
                <Table.Tbody>
                  {group.members.map((member) => {
                    const row = memberRow(member);
                    return (
                      <Table.Tr key={row.name}>
                        <Table.Td>{row.name}</Table.Td><Table.Td>{row.age}</Table.Td><Table.Td>{row.sharing}</Table.Td>
                      </Table.Tr>
                    );
                  })}
                </Table.Tbody>
              </Table>
              <Row gap={8} grow>
                <Button variant="default" size="md" loading={share.pending}
                  onClick={() => share.run({ id: group.id, shares: !mine })}>
                  {mine ? 'Dejar de compartir mis turnos' : 'Compartir mis turnos'}
                </Button>
                <Button variant="default" color="red" size="md" loading={leave.pending} onClick={() => leave.run(group.id)}>
                  Salir del grupo
                </Button>
              </Row>
            </Stack>
          </Paper>
        );
      })}

      {profile.data?.ageBand === 'ADULT' && (
        <Stack gap={12} component="section" aria-label="Registrar a un hijo menor de 13">
          <Title order={2} fz={20}>Registrar a un hijo menor de 13</Title>
          <Text c="dimmed" fz={15}>Lo registramos al instante, en tu grupo, con el vínculo sin probar: hasta que vengan a
            recepción con su documento y lo que prueba que sos su madre, padre o tutor, tiene un turno por delante.</Text>
          <form onSubmit={submit}>
            <Stack gap={14}>
              <Said error={register.error} notice={register.notice} />
              <TextInput label="Nombre completo" required maxLength={150} size="md" value={child.fullName}
                onChange={set('fullName')} />
              <NativeSelect label="Tipo de documento" data={documentTypes} size="md" value={child.documentType}
                onChange={set('documentType')} />
              <TextInput label="Número" required maxLength={20} size="md" value={child.documentNumber}
                onChange={set('documentNumber')} />
              <TextInput label="Fecha de nacimiento" type="date" required size="md" value={child.birthDate}
                onChange={set('birthDate')} />
              <NativeSelect label="Sos su" size="md" value={child.relation} onChange={set('relation')} data={RELATIONS} />
              <NativeSelect label="En el grupo" size="md" value={groupId} onChange={set('groupId')}
                data={[...groups.data.map((group) => ({ value: group.id, label: group.name })),
                  { value: '', label: 'Un grupo nuevo' }]} />
              <Button type="submit" size="md" loading={register.pending}>Registrar</Button>
            </Stack>
          </form>
        </Stack>
      )}

      {groups.data.length > 0 && (
        <Text c="dimmed" fz={14}>Desde los 18, sacás, cancelás y ves los turnos de quienes los comparten, en{' '}
          <Anchor component={Link} to="/appointments">Mis turnos</Anchor>. Los de un menor de 13 se comparten siempre;
          desde los 13, cada uno elige. Para sumar a alguien o sacar a un menor, acercate a recepción.</Text>
      )}
    </Stack>
  );
}
