import { Button, Chip, Group, NativeSelect, Paper, Radio, SegmentedControl, Stack, Text, Textarea, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

import { combine } from '../../shared/refusals';
import { type Contact, ContactFields, contactOf, type DocumentInput, DocumentFields, NO_CONTACT } from '../components/fields';
import { SearchBox, usePatientSearch } from '../components/PatientSearch';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import { documentOf, type Practitioner } from '../types';
import { AGENDA_REFUSALS, NEW_PATIENT_REFUSALS } from '../words';
import { type FreeTimes, hourOf, missing, noTimesSaid, startOf } from './booking';

/**
 * Booking an appointment (D-35, D-45, D-75, D-100): the practitioner, the day and the reason bring the free times;
 * the patient is one found, or a new one registered in the same step, with their phone and email saying whose each
 * is. Nothing chosen is lost while choosing the rest, and a refusal leaves the form as it was.
 */
export function BookingPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [practitionerId, setPractitionerId] = useState(params.get('practitionerId') ?? '');
  const [date, setDate] = useState(params.get('date') ?? '');
  const [reasonId, setReasonId] = useState('');
  const [time, setTime] = useState<string | null>(null);
  const [otherTime, setOtherTime] = useState('');
  const [registering, setRegistering] = useState(false);
  const [query, setQuery] = useState('');
  const [patientId, setPatientId] = useState<string | null>(null);
  const [newName, setNewName] = useState('');
  const [document, setDocument] = useState<DocumentInput>({ type: '', number: '' });
  const [phone, setPhone] = useState<Contact>(NO_CONTACT);
  const [email, setEmail] = useState<Contact>(NO_CONTACT);
  const [note, setNote] = useState('');
  const [why, setWhy] = useState<string | null>(null);

  const practitioners = useQuery({ queryKey: ['practitioners'], queryFn: () => api.get<Practitioner[]>('/api/practitioners') });
  const practitioner = practitioners.data?.find((each) => each.id === practitionerId);
  const reason = practitioner?.reasons.find((each) => each.id === reasonId) ?? practitioner?.reasons[0];
  const freeTimes = useQuery({
    queryKey: ['free-times', practitionerId, reason?.id, date],
    queryFn: () => api.get<FreeTimes>('/api/agenda/free-times', { practitionerId, reasonId: reason?.id, date }),
    enabled: Boolean(practitioner && reason && date),
  });
  const found = usePatientSearch(query);
  const start = date ? startOf(date, time, otherTime) : null;

  const book = useAction(
    () => registering
      ? api.post('/api/appointments/with-new-patient', {
        patient: {
          fullName: newName.trim(),
          documentType: document.type || null,
          documentNumber: document.number.trim() || null,
          phone: contactOf(phone, 'value'),
          email: contactOf(email, 'value'),
        },
        practitionerId, reasonId: reason?.id, startsAt: start, note: note.trim() || null,
      })
      : api.post('/api/appointments', {
        patientId, practitionerId, reasonId: reason?.id, startsAt: start, note: note.trim() || null,
      }),
    {
      refusals: combine(AGENDA_REFUSALS, NEW_PATIENT_REFUSALS),
      invalidates: ['agenda', 'free-times'],
      onDone: () => navigate(`/agenda?date=${date}&practitionerId=${practitionerId}`, {
        state: { notice: registering ? 'Paciente registrado y turno reservado.' : 'Turno reservado.' },
      }),
    },
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    const lacking = missing({ practitionerId, reasonId: reason?.id ?? '', start, patientId, newPatientName: newName,
      registering });
    setWhy(lacking);
    if (!lacking) {
      book.run(undefined);
    }
  }

  return (
    <form onSubmit={submit}>
      <Stack gap={20} maw={880}>
        <Title order={1} fz={28}>{practitioner ? `Nuevo turno con ${practitioner.fullName}` : 'Nuevo turno'}</Title>

        <Paper className="card" p={20}>
          <Group align="flex-end" wrap="wrap">
            <NativeSelect label="Profesional" value={practitionerId}
              onChange={(event) => { setPractitionerId(event.currentTarget.value); setReasonId(''); setTime(null); }}
              data={[{ value: '', label: '— elegir —' },
                ...(practitioners.data ?? []).map((each) => ({ value: each.id, label: each.fullName }))]} />
            <TextInput label="Día" type="date" value={date}
              onChange={(event) => { setDate(event.currentTarget.value); setTime(null); }} />
            {practitioner && (
              <NativeSelect label="Razón" value={reason?.id ?? ''}
                onChange={(event) => { setReasonId(event.currentTarget.value); setTime(null); }}
                data={practitioner.reasons.map((each) => ({
                  value: each.id, label: `${each.name} (${each.durationMinutes} min)` }))} />
            )}
          </Group>
        </Paper>

        <Paper className="card" p={20}>
          <Stack>
            <Group justify="space-between">
              <Title order={2} fz={18}>Paciente</Title>
              <SegmentedControl value={registering ? 'new' : 'found'}
                onChange={(value) => setRegistering(value === 'new')}
                data={[{ value: 'found', label: 'Registrado' }, { value: 'new', label: 'Paciente nuevo' }]} />
            </Group>
            {registering ? (
              <Stack>
                <Text c="dimmed" fz={14}>Se registra al reservar: si el turno no se puede reservar, no se registra.</Text>
                <TextInput label="Nombre completo" maxLength={150} value={newName}
                  onChange={(event) => setNewName(event.currentTarget.value)} />
                <DocumentFields value={document} onChange={setDocument} />
                <ContactFields label="Teléfono" type="tel" value={phone} onChange={setPhone} />
                <ContactFields label="Email" type="email" value={email} onChange={setEmail} />
              </Stack>
            ) : (
              <Stack>
                <SearchBox onSearch={setQuery} />
                {query && found.data?.length === 0 && (
                  <Text c="dimmed">Ningún paciente coincide: cargalo como paciente nuevo.</Text>
                )}
                {found.data && found.data.length > 0 && (
                  <Radio.Group value={patientId} onChange={setPatientId} label="Elegí al paciente">
                    <Stack gap={6} mt={6}>
                      {found.data.map((each) => (
                        <Radio key={each.id} value={each.id}
                          label={documentOf(each) ? `${each.fullName} — ${documentOf(each)}` : each.fullName} />
                      ))}
                    </Stack>
                  </Radio.Group>
                )}
              </Stack>
            )}
          </Stack>
        </Paper>

        <Paper className="card" p={20}>
          <Stack>
            <Title order={2} fz={18}>Horario</Title>
            {!practitioner && <Text c="dimmed">Elegí el profesional para ver sus horarios libres.</Text>}
            {practitioner && practitioner.reasons.length === 0 && (
              <Text c="dimmed">Este profesional todavía no tiene razones cargadas.</Text>
            )}
            {practitioner && !date && <Text c="dimmed">Elegí el día.</Text>}
            {freeTimes.data && practitioner && (
              <>
                {noTimesSaid(freeTimes.data, practitioner.fullName) && (
                  <Text c="dimmed">{noTimesSaid(freeTimes.data, practitioner.fullName)}</Text>
                )}
                <Chip.Group value={time} onChange={(value) => setTime(value as string)}>
                  <Group gap={6} role="group" aria-label="Horarios libres">
                    {freeTimes.data.times.map((each) => (
                      <Chip key={each} value={each} variant="outline" className="mono">{hourOf(each)}</Chip>
                    ))}
                  </Group>
                </Chip.Group>
              </>
            )}
            <TextInput label="…u otra hora" type="time" w={160} value={otherTime}
              onChange={(event) => setOtherTime(event.currentTarget.value)} />
            <Textarea label="Nota (opcional)" maxLength={500} value={note}
              onChange={(event) => setNote(event.currentTarget.value)} />
          </Stack>
        </Paper>

        <Said error={why ?? book.error} />
        <Group>
          <Button type="submit" loading={book.pending}>Reservar</Button>
          <Button variant="default"
            onClick={() => navigate(`/agenda?${new URLSearchParams({ ...(date && { date }),
              ...(practitionerId && { practitionerId }) })}`)}>
            Volver
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
