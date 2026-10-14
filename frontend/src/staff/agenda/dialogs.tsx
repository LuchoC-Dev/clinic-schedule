import { Button, Group, Modal, NativeSelect, Stack, Text, Textarea } from '@mantine/core';
import { type FormEvent, useState } from 'react';

import { timeOf } from '../../shared/dates';
import { combine } from '../../shared/refusals';
import { ChargeDialog } from '../components/ChargeDialog';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import { AGENDA_REFUSALS, ARRIVAL_REFUSALS } from '../words';
import { type AgendaAppointment, patientName } from './agenda';

function heading(appointment: AgendaAppointment) {
  return `${patientName(appointment)} · ${appointment.practitioner ?? ''} · ${timeOf(appointment.startsAt)} · ${appointment.reason}`;
}

/**
 * The patient arrived: they come into their practitioner's queue (D-65), their visit charged in the same step by
 * the means reception picks (D-87, D-89), or covered by their insurance, the amount then the copay (D-90).
 */
export function ArrivalDialog({ appointment, onClose, onDone }: {
  appointment: AgendaAppointment;
  onClose: () => void;
  onDone?: () => void;
}) {
  return (
    <ChargeDialog title="Llegó" about={heading(appointment)} button="Llegó" refusals={ARRIVAL_REFUSALS}
      invalidates={['agenda', 'session']} onClose={onClose} onDone={onDone}
      send={(charge) => api.post('/api/appointment-arrivals', { appointmentId: appointment.id, ...charge })} />
  );
}

/** Cancelling keeps the appointment, saying who asked for it, with reception's note (D-76). */
export function CancelDialog({ appointment, onClose }: { appointment: AgendaAppointment; onClose: () => void }) {
  const [requestedBy, setRequestedBy] = useState('');
  const [note, setNote] = useState('');
  const cancel = useAction(
    () => api.post(`/api/appointments/${appointment.id}/cancel`, { requestedBy, note: note.trim() || null }),
    { refusals: combine(AGENDA_REFUSALS), invalidates: ['agenda', 'patient-appointments'], onDone: onClose },
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    cancel.run(undefined);
  }

  return (
    <Modal opened onClose={onClose} title="Cancelar turno" centered>
      <form onSubmit={submit}>
        <Stack>
          <Text c="dimmed" fz={14}>{heading(appointment)}</Text>
          <NativeSelect label="Lo pidió" required value={requestedBy}
            onChange={(event) => setRequestedBy(event.currentTarget.value)}
            data={[{ value: '', label: 'Elegí quién lo pidió' }, { value: 'PATIENT', label: 'el paciente' },
              { value: 'PRACTICE', label: 'el consultorio' }]} />
          <Textarea label="Nota (opcional)" maxLength={200} value={note}
            onChange={(event) => setNote(event.currentTarget.value)} />
          <Said error={cancel.error} />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>Volver</Button>
            <Button type="submit" color="red" loading={cancel.pending} disabled={!requestedBy}>Cancelar turno</Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
