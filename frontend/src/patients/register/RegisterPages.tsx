import { Alert, Anchor, Button, Fieldset, Loader, NativeSelect, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { type FormEvent, useContext, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';

import { ApiError } from '../../shared/api';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { mismatch } from '../account/account';
import { useLinkCheck } from '../account/useLinkCheck';
import { api, SessionContext } from '../session';
import {
  type ChildForm, DOCUMENT_KNOWN, emptyRegistration, RELATIONS, registrationBody, registrationRefusals,
  UNUSABLE_CONFIRMATION,
} from './register';

/**
 * Registering on the web (D-91, D-92), with nobody logged in: an adult of 18 or older, and up to three children under
 * 13; a block left empty is no child. What was typed stays on a refusal; the passwords never leave the form.
 */
export function RegisterPage() {
  const { documentTypes } = useContext(SessionContext);
  const navigate = useNavigate();
  const [form, setForm] = useState(() => emptyRegistration(documentTypes[0] ?? 'DNI'));
  const [typo, setTypo] = useState<string | null>(null);
  const register = useAction(() => api.post('/api/portal/registrations', registrationBody(form)), {
    refusals: registrationRefusals(form),
    onDone: () => navigate('/register/sent', { state: { email: form.email.trim() } }),
  });
  const set = (key: 'fullName' | 'documentType' | 'documentNumber' | 'birthDate' | 'phone' | 'email' | 'password'
    | 'repeated') => (event: { currentTarget: { value: string } }) => {
    const { value } = event.currentTarget;
    setForm((before) => ({ ...before, [key]: value }));
  };
  const setChild = (index: number, key: keyof ChildForm) => (event: { currentTarget: { value: string } }) => {
    const { value } = event.currentTarget;
    setForm((before) => ({
      ...before,
      children: before.children.map((child, at) => (at === index ? { ...child, [key]: value } : child)),
    }));
  };

  function submit(event: FormEvent) {
    event.preventDefault();
    const wrong = mismatch(form.password, form.repeated);
    setTypo(wrong);
    if (!wrong) {
      register.run(undefined);
    }
  }

  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>Registrarte</Title>
      <Text c="dimmed">Para mayores de 18 años. Te mandamos un link a tu email para confirmar el registro; cuando vengas
        por primera vez, traé tu documento.</Text>
      <form onSubmit={submit}>
        <Stack gap={16}>
          <TextInput label="Nombre completo" required maxLength={150} autoComplete="name" size="md"
            value={form.fullName} onChange={set('fullName')} />
          <NativeSelect label="Tipo de documento" data={documentTypes} size="md" value={form.documentType}
            onChange={set('documentType')} />
          <TextInput label="Número" required maxLength={20} size="md" value={form.documentNumber}
            onChange={set('documentNumber')} />
          <TextInput label="Fecha de nacimiento" type="date" required size="md" value={form.birthDate}
            onChange={set('birthDate')} />
          <TextInput label="Teléfono (opcional)" type="tel" maxLength={30} autoComplete="tel" size="md"
            value={form.phone} onChange={set('phone')} />
          <TextInput label="Email" type="email" required maxLength={254} autoComplete="email" size="md"
            value={form.email} onChange={set('email')} />
          <PasswordInput label="Contraseña, de 10 a 72 caracteres" autoComplete="new-password" required minLength={10}
            size="md" value={form.password} onChange={set('password')} />
          <PasswordInput label="Repetila" autoComplete="new-password" required minLength={10} size="md"
            value={form.repeated} onChange={set('repeated')} />

          <Title order={2} fz={20}>Tus hijos menores de 13, si querés</Title>
          <Text c="dimmed" fz={15}>Desde tu cuenta vas a ver, sacar y cancelar sus turnos. Cuando vengan, traé sus
            documentos y lo que prueba que sos su madre, padre o tutor. Dejá vacío lo que no uses.</Text>
          {form.children.map((child, index) => (
            <Fieldset key={index} legend={`Hijo ${index + 1}`}>
              <Stack gap={12}>
                <TextInput label="Nombre completo" maxLength={150} size="md" value={child.fullName}
                  onChange={setChild(index, 'fullName')} />
                <NativeSelect label="Tipo de documento" data={documentTypes} size="md" value={child.documentType}
                  onChange={setChild(index, 'documentType')} />
                <TextInput label="Número" maxLength={20} size="md" value={child.documentNumber}
                  onChange={setChild(index, 'documentNumber')} />
                <TextInput label="Fecha de nacimiento" type="date" size="md" value={child.birthDate}
                  onChange={setChild(index, 'birthDate')} />
                <NativeSelect label="Sos su" size="md" value={child.relation} onChange={setChild(index, 'relation')}
                  data={RELATIONS} />
              </Stack>
            </Fieldset>
          ))}
          <Said error={typo ?? register.error} />
          <Button type="submit" size="md" loading={register.pending}>Registrarme</Button>
        </Stack>
      </form>
      <Anchor component={Link} to="/login">Ya tengo cuenta: ingresar</Anchor>
    </Stack>
  );
}

/** The same page whether the document was known or not (D-91). */
export function RegisterSentPage() {
  const email = (useLocation().state as { email?: string } | null)?.email;
  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>Revisá tu email</Title>
      <Text>Te mandamos un email{email && <> a <strong>{email}</strong></>}. Seguí lo que dice para terminar tu
        registro.</Text>
      <Text c="dimmed">Si no te llega en unos minutos, mirá en el correo no deseado.</Text>
      <Anchor component={Link} to="/login">Ingresar</Anchor>
    </Stack>
  );
}

/**
 * The registration's link opens this page, whose button confirms it: mail readers open links on their own, and
 * opening one changes nothing (D-91). The login says afterwards which children it could not register (D-92).
 */
export function RegisterConfirmPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const link = useLinkCheck('/api/portal/registrations/confirmation', token);
  const confirm = useAction(() => api.post<{ notRegistered: string[] }>('/api/portal/registrations/confirmation',
    { token }), {
    onDone: (confirmed) => navigate('/login?registered', { state: { notRegistered: confirmed.notRegistered } }),
  });
  const failure = confirm.failure instanceof ApiError ? confirm.failure : null;
  const refused = failure?.status === 409 ? DOCUMENT_KNOWN
    : link.unusable || failure?.status === 400 ? UNUSABLE_CONFIRMATION : null;

  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>Confirmá tu registro</Title>
      {refused ? <Alert color="red" role="alert">{refused}</Alert>
        : link.error ? <Alert color="red" role="alert">{link.error}</Alert>
          : !link.usable ? <Loader size="sm" aria-label="Revisando el link" />
            : (
              <>
                <Text c="dimmed">Al confirmarlo abrimos tu cuenta: vas a ingresar con tu documento y la contraseña
                  que elegiste.</Text>
                <Said error={confirm.error} />
                <Button size="md" loading={confirm.pending} onClick={() => confirm.run(undefined)}>
                  Confirmar registro
                </Button>
              </>
            )}
      <Anchor component={Link} to="/login">Ingresar</Anchor>
    </Stack>
  );
}
