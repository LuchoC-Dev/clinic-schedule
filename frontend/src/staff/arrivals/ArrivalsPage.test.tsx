import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { appointment } from '../../test/agenda';
import { api } from '../session';
import type { Entry, Session } from './arrivals';
import { ArrivalsPage } from './ArrivalsPage';

vi.mock('../session', () => ({ api: { get: vi.fn(), post: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

const QUEUE = { id: 'q1', name: 'Consultorio 1', room: '1', practitionerIds: ['d1', 'd2'] };

function entry(overrides: Partial<Entry>): Entry {
  return {
    id: 'e1', patientId: 'p1', patient: 'Ana Pérez', patientDocument: 'DNI 30111222', arrivedAt: '2026-03-10T09:10:00',
    emergency: false, appointmentId: null, appointmentAt: null, reservedForId: null, reservedFor: null, state: 'WAITING',
    timesCalled: 0, withPractitionerId: null, withPractitioner: null, ...overrides,
  };
}

function session(overrides: Partial<Session> = {}): Session {
  return {
    queueId: 'q1', open: true, closesByItself: true,
    waiting: [
      entry({ id: 'e1', emergency: true }),
      entry({ id: 'e2', patientId: 'p2', patient: 'Juan Gómez', patientDocument: null, appointmentAt: '2026-03-10T10:00:00',
        reservedForId: 'd1', reservedFor: 'Dra. Arias', timesCalled: 2 }),
    ],
    uncallable: [],
    called: [entry({ id: 'e3', patientId: 'p3', patient: 'Rosa Díaz', state: 'CALLED', withPractitioner: 'Dr. Benítez' })],
    done: [entry({ id: 'e4', patientId: 'p4', patient: 'Luis Paz', state: 'LEFT' })],
    ...overrides,
  };
}

function answering(current: Session) {
  get.mockImplementation(((path: string) => {
    if (path === '/api/queues') return Promise.resolve([QUEUE]);
    if (path === '/api/practitioners') {
      return Promise.resolve([{ id: 'd1', fullName: 'Dra. Arias', reasons: [] }, { id: 'd2', fullName: 'Dr. Benítez', reasons: [] }]);
    }
    if (path === '/api/queues/q1/session') return Promise.resolve(current);
    if (path === '/api/agenda') {
      return Promise.resolve({ date: '2026-03-10', from: '2026-03-10', to: '2026-03-10', practitionerId: null,
        practitioners: [], appointments: [appointment({ id: 'a9', patientId: 'p9', practitionerId: 'd2',
          startsAt: '2026-03-10T11:30:00' })] });
    }
    if (path === '/api/patients') {
      return Promise.resolve([{ id: 'p9', fullName: 'Sofía Ruiz', document: null }]);
    }
    return Promise.reject(new Error(path));
  }) as typeof api.get);
}

function show() {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={['/arrivals']}>
          <Routes><Route path="/arrivals" element={<ArrivalsPage />} /></Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

const rowOf = (text: string) => screen.getByText(text).closest('tr') as HTMLElement;

describe('the arrivals', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    post.mockResolvedValue(session());
  });

  it('shows who waits in order, who was called and who is done, with their names', async () => {
    answering(session());
    show();

    expect(await screen.findByText('Ana Pérez — DNI 30111222')).toBeInTheDocument();
    expect(screen.getByText('Abierta')).toBeInTheDocument();
    expect(screen.getByText('Se cierra sola al terminar el día.')).toBeInTheDocument();
    const juan = rowOf('Juan Gómez');
    expect(within(juan).getByText('2')).toBeInTheDocument();
    expect(within(juan).getByText(/Turno 10:00/)).toBeInTheDocument();
    expect(within(juan).getByText(/espera a Dra. Arias/)).toBeInTheDocument();
    expect(within(juan).getByText('Llamado 2 veces')).toBeInTheDocument();
    expect(within(rowOf('Ana Pérez — DNI 30111222')).getByText(/Urgencia/)).toBeInTheDocument();
    expect(within(rowOf('Ana Pérez — DNI 30111222')).queryByRole('button', { name: 'Urgencia' })).not.toBeInTheDocument();
    expect(within(rowOf('Rosa Díaz — DNI 30111222')).getByText('Dr. Benítez')).toBeInTheDocument();
    expect(within(rowOf('Luis Paz — DNI 30111222')).getByText('Se fue')).toBeInTheDocument();
  });

  /** Given to whoever serves the queue but the one they wait for, or to anyone (D-64). */
  it('gives a waiting patient to another practitioner, frees them, makes them an emergency, or says they left', async () => {
    answering(session());
    show();
    const juan = await screen.findByText('Juan Gómez').then((name) => name.closest('tr') as HTMLElement);

    expect(within(juan).getAllByRole('option').map((option) => option.textContent)).toEqual(['Dr. Benítez']);
    await userEvent.click(within(juan).getByRole('button', { name: 'Dar a' }));
    expect(post).toHaveBeenLastCalledWith('/api/queue-entries/e2/reassign', { practitionerId: 'd2' });
    expect(await screen.findByRole('status')).toHaveTextContent('Paciente asignado a otro profesional, en el mismo lugar.');
    // Ana waits for nobody: either practitioner, the one chosen.
    const ana = rowOf('Ana Pérez — DNI 30111222');
    await userEvent.selectOptions(within(ana).getByRole('combobox'), 'd2');
    await userEvent.click(within(ana).getByRole('button', { name: 'Dar a' }));
    expect(post).toHaveBeenLastCalledWith('/api/queue-entries/e1/reassign', { practitionerId: 'd2' });
    await userEvent.click(within(juan).getByRole('button', { name: 'Quitar reserva' }));
    expect(post).toHaveBeenLastCalledWith('/api/queue-entries/e2/lift-reservation', undefined);
    await userEvent.click(within(juan).getByRole('button', { name: 'Urgencia' }));
    expect(post).toHaveBeenLastCalledWith('/api/queue-entries/e2/emergency', undefined);
    await userEvent.click(within(juan).getByRole('button', { name: 'Se fue' }));
    expect(post).toHaveBeenLastCalledWith('/api/queue-entries/e2/left', undefined);
  });

  it('says who nobody can call, kept for a practitioner gone from the queue', async () => {
    answering(session({ uncallable: [entry({ id: 'e5', patient: 'Ema Sol', patientDocument: null, reservedFor: 'Dr. Ido' })] }));
    show();

    expect(await screen.findByRole('alert')).toHaveTextContent('Nadie puede llamar a Ema Sol (espera a Dr. Ido)');
  });

  it('opens a closed queue', async () => {
    answering(session({ open: false, waiting: [], called: [], done: [] }));
    show();

    await userEvent.click(await screen.findByRole('button', { name: 'Abrir' }));
    expect(post).toHaveBeenCalledWith('/api/queues/q1/open');
    expect(await screen.findByRole('status')).toHaveTextContent('Cola abierta.');
    expect(screen.queryByText('Sumar a la cola')).not.toBeInTheDocument();
  });

  /** Coming in with today's appointment, charged as they come in (D-64, D-87). */
  it("brings a patient found in with today's appointment, charged", async () => {
    answering(session());
    show();
    await screen.findByText('Juan Gómez');

    await userEvent.type(screen.getByLabelText('Buscar paciente'), 'Sofía');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Llegó con turno 11:30' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/Importe/), '15000');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Llegó' }));

    expect(post).toHaveBeenCalledWith('/api/appointment-arrivals',
      { appointmentId: 'a9', amount: 15000, means: 'CASH', coverage: undefined });
    expect(await screen.findByRole('status')).toHaveTextContent('Paciente con turno sumado a la cola de su profesional.');
  });

  /** An emergency may come in with nothing charged, left pending (D-87). */
  it('brings a patient in as an emergency with nothing charged', async () => {
    answering(session());
    show();
    await screen.findByText('Juan Gómez');

    await userEvent.type(screen.getByLabelText('Buscar paciente'), 'Sofía');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByRole('button', { name: 'Sumar sin turno' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sumar como urgencia' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sumar' }));

    expect(post).toHaveBeenCalledWith('/api/queues/q1/session/patients',
      { patientId: 'p9', emergency: true, amount: undefined, means: 'CASH', coverage: undefined });
  });

  it('registers one who gives no name as an emergency, and says a refusal by its code', async () => {
    answering(session());
    post.mockRejectedValueOnce(new ApiError(409, { status: 409, error: 'x', message: 'x', code: 'QUEUE_NOT_OPEN' }));
    show();
    await screen.findByText('Juan Gómez');

    await userEvent.click(screen.getByRole('button', { name: 'Sumar sin datos (NN)' }));
    let dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/Importe/), '15000');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Registrar y sumar' }));
    expect(post).toHaveBeenLastCalledWith('/api/queues/q1/session/unidentified',
      { emergency: false, amount: 15000, means: 'CASH' });
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('La cola está cerrada: abrila primero.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    await userEvent.click(screen.getByRole('button', { name: 'Urgencia sin datos (NN)' }));
    dialog = await screen.findByRole('dialog');
    expect(within(dialog).queryByLabelText('Con cobertura')).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Registrar y sumar' }));
    expect(post).toHaveBeenLastCalledWith('/api/queues/q1/session/unidentified',
      { emergency: true, amount: undefined, means: 'CASH' });
  });
});
