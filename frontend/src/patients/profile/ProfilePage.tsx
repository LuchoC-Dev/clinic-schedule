import { Anchor, Button, Loader, NativeSelect, Paper, Stack, Text, TextInput, Title } from '@mantine/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link } from 'react-router';

import { describeError } from '../../shared/errors';
import { byCode } from '../../shared/refusals';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import {
  birthDateSaid, consentChoices, consentValue, CONSENT_REFUSED, detailsBody, type DetailsForm, detailsFormOf,
  DETAILS_REFUSED, type Profile, RELATIONS,
} from './profile';

const OWNERS = [{ value: '', label: 'Mío' }, ...RELATIONS];

/** Each channel's reminders as they stand, the first of its choices. */
function consentOf(profile: Profile) {
  return { phone: consentChoices(profile.phone)[0]?.value, email: consentChoices(profile.email)[0]?.value };
}

/**
 * *Mis datos* (D-81): the patient's name, document and date of birth, which reception changes; their contact and
 * health insurance, which they change with reception's rules; and their reminders, each channel apart. What was
 * typed stays on a refusal; once saved, the form is what the server keeps.
 */
export function ProfilePage() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: () => api.get<Profile>('/api/portal/profile') });
  if (profile.isError) {
    return <Said error={describeError(profile.error)} />;
  }
  if (!profile.data) {
    return <Loader size="sm" aria-label="Buscando tus datos" />;
  }
  return <ProfileForms profile={profile.data} />;
}

function ProfileForms({ profile }: { profile: Profile }) {
  const queries = useQueryClient();
  const [details, setDetails] = useState<DetailsForm>(() => detailsFormOf(profile));
  const phoneChoices = consentChoices(profile.phone);
  const emailChoices = consentChoices(profile.email);
  const [consent, setConsent] = useState(() => consentOf(profile));
  // Each save answers the data as kept: both forms start again from it.
  const kept = (answer: Profile) => {
    queries.setQueryData(['profile'], answer);
    setDetails(detailsFormOf(answer));
    setConsent(consentOf(answer));
  };
  const saveDetails = useAction(() => api.put<Profile>('/api/portal/profile/details', detailsBody(details)), {
    refusals: byCode({ OWN_DATA_REFUSED: DETAILS_REFUSED }), done: 'Tus datos quedaron guardados.', onDone: kept,
  });
  const saveConsent = useAction(() => api.put<Profile>('/api/portal/profile/contact-consent', {
    // A channel not there has no choice, and is left as it is.
    phone: consentValue(consent.phone),
    email: consentValue(consent.email),
  }), { refusals: byCode({ OWN_DATA_REFUSED: CONSENT_REFUSED }), done: 'Tus recordatorios quedaron guardados.', onDone: kept });
  const set = (key: keyof DetailsForm) => (event: { currentTarget: { value: string } }) => {
    const { value } = event.currentTarget;
    setDetails((before) => ({ ...before, [key]: value }));
  };

  function submitDetails(event: FormEvent) {
    event.preventDefault();
    saveDetails.run(undefined);
  }

  function submitConsent(event: FormEvent) {
    event.preventDefault();
    saveConsent.run(undefined);
  }

  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>Mis datos</Title>
      <Text><strong>{profile.fullName}</strong> · {profile.document}
        {profile.birthDate && ` · nacimiento ${birthDateSaid(profile.birthDate)}`}</Text>
      <Text c="dimmed" fz={14}>Para corregir tu nombre, tu documento o tu fecha de nacimiento, acercate a recepción con
        tu documento.</Text>

      <Title order={2} fz={20}>Contacto y obra social</Title>
      <Paper className="card" p={16}>
        <form onSubmit={submitDetails}>
          <Stack gap={14}>
            <Said error={saveDetails.error} notice={saveDetails.notice} />
            <TextInput label="Teléfono" type="tel" maxLength={20} size="md" value={details.phoneNumber}
              onChange={set('phoneNumber')} />
            <NativeSelect label="El teléfono es de" size="md" data={OWNERS} value={details.phoneOwnerRelation}
              onChange={set('phoneOwnerRelation')} />
            <TextInput label="Su nombre, si el teléfono no es tuyo" maxLength={150} size="md" value={details.phoneOwnerName}
              onChange={set('phoneOwnerName')} />
            <TextInput label="Email" type="email" maxLength={254} size="md" value={details.emailAddress}
              onChange={set('emailAddress')} />
            <NativeSelect label="El email es de" size="md" data={OWNERS} value={details.emailOwnerRelation}
              onChange={set('emailOwnerRelation')} />
            <TextInput label="Su nombre, si el email no es tuyo" maxLength={150} size="md" value={details.emailOwnerName}
              onChange={set('emailOwnerName')} />
            <TextInput label="Obra social o prepaga" maxLength={100} size="md" value={details.insurer}
              onChange={set('insurer')} />
            <TextInput label="Número de afiliado" maxLength={30} size="md" value={details.memberNumber}
              onChange={set('memberNumber')} />
            <TextInput label="Plan" maxLength={50} size="md" value={details.plan} onChange={set('plan')} />
            <Button type="submit" size="md" loading={saveDetails.pending}>Guardar</Button>
          </Stack>
        </form>
      </Paper>
      <Text c="dimmed" fz={14}>Si cambiás un teléfono o un email, o de quién es, tenés que volver a autorizar los
        recordatorios ahí.</Text>

      <Title order={2} fz={20}>Recordatorios de tus turnos</Title>
      {phoneChoices.length === 0 && emailChoices.length === 0 ? (
        <Text c="dimmed">Cargá un teléfono o un email para recibir recordatorios.</Text>
      ) : (
        <Paper className="card" p={16}>
          <form onSubmit={submitConsent}>
            <Stack gap={14}>
              <Said error={saveConsent.error} notice={saveConsent.notice} />
              {phoneChoices.length > 0 && (
                <NativeSelect label={`Al teléfono ${profile.phone?.value}`} size="md" data={phoneChoices}
                  value={consent.phone} onChange={(event) => {
                    const { value } = event.currentTarget;
                    setConsent((before) => ({ ...before, phone: value as typeof before.phone }));
                  }} />
              )}
              {emailChoices.length > 0 && (
                <NativeSelect label={`Al email ${profile.email?.value}`} size="md" data={emailChoices}
                  value={consent.email} onChange={(event) => {
                    const { value } = event.currentTarget;
                    setConsent((before) => ({ ...before, email: value as typeof before.email }));
                  }} />
              )}
              <Button type="submit" size="md" loading={saveConsent.pending}>Guardar</Button>
            </Stack>
          </form>
        </Paper>
      )}

      <Anchor component={Link} to="/password">Cambiar tu contraseña</Anchor>
    </Stack>
  );
}
