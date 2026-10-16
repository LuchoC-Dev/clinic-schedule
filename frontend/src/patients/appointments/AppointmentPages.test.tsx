import { QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { api } from '../session';
import type { BookingOptions, Entry, MoveTarget, OwnAppointments } from './appointments';
import { AppointmentsPage } from './AppointmentsPage';
import { BookingPage } from './BookingPage';
import { MovePage } from './MovePage';

vi.mock('../session', () => ({ api: { get: vi.fn(), post: vi.fn() } }));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

const refusal = (code: string) => new ApiError(409, { status: 409, error: 'Conflict', message: 'in English', code });

function Address() {
  const location = useLocation();
  return <p data-testid="address">{location.pathname + location.search}</p>;
}

function show(address: string) {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={[address]}>
          <Routes>
            <Route path="/appointments" element={<AppointmentsPage />} />
            <Route path="/appointments/:id/move" element={<MovePage />} />
            <Route path="/booking" element={<BookingPage />} />
          </Routes>
          <Address />
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

function answer(paths: Record<string, unknown>) {
  get.mockImplementation(((path: string, query?: Record<string, string>) => {
    const key = query ? `${path}?${new URLSearchParams(query)}` : path;
    return key in paths ? Promise.resolve(paths[key]) : Promise.reject(new Error(key));
  }) as typeof api.get);
}

const address = () => screen.getByTestId('address').textContent;

function entry(id: string, startsAt: string, changes: Partial<Entry> = {}): Entry {
  return {
    id, startsAt, practitioner: { id: 'p1', fullName: 'Dra. Arias' }, reason: 'consulta', state: 'BOOKED',
    cancellationRequestedBy: null, cancellable: true, movable: true, ...changes,
  };
}

const MINE: OwnAppointments = {
  upcoming: [entry('a1', '2026-03-12T10:00:00'), entry('a2', '2026-03-10T20:00:00', { cancellable: false, movable: false }),
    entry('a6', '2026-03-20T10:00:00', { cancellable: false })],
  past: [entry('a3', '2026-03-02T10:00:00', { state: 'ABSENT', cancellable: false, movable: false })],
  family: [{ patientId: 'k1', fullName: 'Benjamín Medina', upcoming: [entry('a4', '2026-03-16T09:00:00', { movable: false })],
    past: [entry('a5', '2026-02-02T09:00:00', { state: 'CANCELLED', cancellationRequestedBy: 'PATIENT',
      cancellable: false, movable: false })] }],
};

const card = (when: string) => screen.getByRole('article', { name: when });

beforeEach(() => {
  get.mockReset();
  post.mockReset();
});

describe('Mis turnos', () => {
  it("offers changing and cancelling only what the server says may be, the patient's own and a member's", async () => {
    answer({ '/api/portal/appointments': MINE });
    show('/appointments');

    const next = await screen.findByRole('article', { name: 'Jueves 12 de marzo, 10:00' });
    expect(next).toHaveTextContent('Próximo');
    expect(next).toHaveTextContent('Dra. Arias · consulta');
    expect(within(next).getByRole('link', { name: 'Cambiar horario' })).toHaveAttribute('href', '/appointments/a1/move');
    expect(within(next).getByRole('button', { name: 'Cancelar' })).toBeInTheDocument();
    expect(within(card('Martes 10 de marzo, 20:00')).queryByRole('button')).not.toBeInTheDocument();
    expect(within(card('Martes 10 de marzo, 20:00')).queryByRole('link')).not.toBeInTheDocument();
    expect(within(card('Viernes 20 de marzo, 10:00')).getByRole('link', { name: 'Cambiar horario' })).toBeInTheDocument();
    expect(within(card('Viernes 20 de marzo, 10:00')).queryByRole('button', { name: 'Cancelar' })).not.toBeInTheDocument();

    const member = card('Lunes 16 de marzo, 09:00');
    expect(member).toHaveTextContent('Benjamín Medina');
    expect(within(member).getByRole('button', { name: 'Cancelar' })).toBeInTheDocument();
    expect(within(member).queryByRole('link', { name: 'Cambiar horario' })).not.toBeInTheDocument();

    expect(screen.getByRole('region', { name: 'Del último año' })).toHaveTextContent('No asististe');
    expect(screen.getByRole('region', { name: 'Benjamín Medina, del último año' }))
      .toHaveTextContent('Cancelado a pedido del paciente');
  });

  it('cancels one, says so and reads them again; a refusal is said', async () => {
    answer({ '/api/portal/appointments': MINE });
    post.mockResolvedValueOnce(undefined).mockRejectedValueOnce(refusal('TOO_LATE'));
    show('/appointments');

    await userEvent.click(within(await screen.findByRole('article', { name: 'Jueves 12 de marzo, 10:00' }))
      .getByRole('button', { name: 'Cancelar' }));
    expect(post).toHaveBeenCalledWith('/api/portal/appointments/a1/cancel');
    expect(await screen.findByRole('status')).toHaveTextContent('Turno cancelado.');
    expect(get).toHaveBeenCalledTimes(2);

    await userEvent.click(within(card('Lunes 16 de marzo, 09:00')).getByRole('button', { name: 'Cancelar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Falta menos de un día para el turno');
  });

  it('says when there is nothing ahead', async () => {
    answer({ '/api/portal/appointments': { upcoming: [], past: [], family: [] } });
    show('/appointments');
    expect(await screen.findByText('No tenés turnos próximos.')).toBeInTheDocument();
    expect(screen.getByText('No tuviste turnos en el último año.')).toBeInTheDocument();
  });
});

const OPTIONS: BookingOptions = {
  firstDay: '2026-03-11', lastDay: '2026-03-14',
  practitioners: [
    { id: 'p1', fullName: 'Dra. Arias', reasons: [{ id: 'r1', name: 'consulta', durationMinutes: 30 }] },
    { id: 'p2', fullName: 'Dr. Benítez', reasons: [{ id: 'r2', name: 'control', durationMinutes: 20 }] },
  ],
  patients: [{ id: 'me', fullName: 'Laura Medina' }, { id: 'k1', fullName: 'Benjamín Medina' }],
};

describe('booking', () => {
  it('offers whom, the reasons and the days, reads the times of the day chosen and books the one confirmed', async () => {
    answer({
      '/api/portal/booking/options': OPTIONS,
      '/api/portal/booking/times?practitionerId=p1&reasonId=r1&date=2026-03-11': { date: '2026-03-11', times: ['10:00'] },
      '/api/portal/booking/times?practitionerId=p2&reasonId=r2&date=2026-03-11': { date: '2026-03-11', times: [] },
      '/api/portal/booking/times?practitionerId=p2&reasonId=r2&date=2026-03-13':
        { date: '2026-03-13', times: ['09:00', '09:20'] },
    });
    post.mockResolvedValue({ startsAt: '2026-03-13T09:20:00', practitioner: 'Dr. Benítez', confirmationSent: true });
    show('/booking');

    const whom = await screen.findByLabelText('Para');
    expect(within(whom).getAllByRole('option').map((option) => option.textContent))
      .toEqual(['Laura Medina (vos)', 'Benjamín Medina']);
    expect(await screen.findByRole('button', { name: '10:00' })).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Días' })).getAllByRole('button')).toHaveLength(4);

    await userEvent.selectOptions(whom, 'k1');
    await userEvent.selectOptions(screen.getByLabelText('Profesional y motivo'), 'r2');
    expect(await screen.findByText('No quedan horarios libres ese día: probá con otro.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'vie 13' }));
    expect(screen.getByRole('button', { name: 'Elegí un horario' })).toBeDisabled();
    await userEvent.click(await screen.findByRole('button', { name: '09:20' }));
    await userEvent.click(screen.getByRole('button', { name: 'Reservar: Viernes 13 de marzo, 09:20' }));

    expect(post).toHaveBeenCalledWith('/api/portal/appointments',
      { patientId: 'k1', practitionerId: 'p2', reasonId: 'r2', startsAt: '2026-03-13T09:20:00' });
    expect(await screen.findByText(/Turno reservado para Benjamín Medina: Viernes 13 de marzo, 09:20 con Dr. Benítez/))
      .toBeInTheDocument();
    expect(address()).toBe('/appointments');
  });

  it("books the patient's own without saying whom, and names the member a refusal was for", async () => {
    answer({
      '/api/portal/booking/options': OPTIONS,
      '/api/portal/booking/times?practitionerId=p1&reasonId=r1&date=2026-03-11': { date: '2026-03-11', times: ['10:00'] },
    });
    post.mockRejectedValueOnce(refusal('TOO_MANY_AHEAD'));
    show('/booking?patient=k1');

    await userEvent.click(await screen.findByRole('button', { name: '10:00' }));
    await userEvent.click(screen.getByRole('button', { name: /^Reservar/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Benjamín Medina ya tiene 3 turnos por delante');
    expect(address()).toBe('/booking?patient=k1');

    await userEvent.selectOptions(screen.getByLabelText('Para'), 'me');
    await userEvent.click(await screen.findByRole('button', { name: '10:00' }));
    post.mockRejectedValueOnce(refusal('TOO_MANY_AHEAD'));
    await userEvent.click(screen.getByRole('button', { name: /^Reservar/ }));
    expect(post).toHaveBeenLastCalledWith('/api/portal/appointments',
      { patientId: null, practitionerId: 'p1', reasonId: 'r1', startsAt: '2026-03-11T10:00:00' });
    expect(await screen.findByRole('alert')).toHaveTextContent('Ya tenés 3 turnos por delante');
  });

  it('asks nobody for whom when there is only the patient, and says when nothing is booked on the web', async () => {
    answer({
      '/api/portal/booking/options': { ...OPTIONS, patients: [OPTIONS.patients[0]] },
      '/api/portal/booking/times?practitionerId=p1&reasonId=r1&date=2026-03-11': { date: '2026-03-11', times: [] },
    });
    show('/booking');
    expect(await screen.findByLabelText('Profesional y motivo')).toBeInTheDocument();
    expect(screen.queryByLabelText('Para')).not.toBeInTheDocument();
    cleanup();

    answer({ '/api/portal/booking/options': { ...OPTIONS, practitioners: [], patients: [OPTIONS.patients[0]] } });
    show('/booking');
    expect(await screen.findByText(/no hay turnos para sacar por la web/)).toBeInTheDocument();
  });
});

const TARGET: MoveTarget = {
  id: 'a1', member: 'Benjamín Medina', startsAt: '2026-03-12T10:00:00', practitioner: { id: 'p1', fullName: 'Dra. Arias' },
  reason: 'consulta', movable: true, firstDay: '2026-03-11', lastDay: '2026-03-13',
};

describe('changing the time', () => {
  it('shows the appointment as it stands, reads the times of the day chosen and moves it to the one confirmed', async () => {
    answer({
      '/api/portal/appointments/a1': TARGET,
      '/api/portal/appointments/a1/move-times?date=2026-03-11': { date: '2026-03-11', times: ['11:00'] },
      '/api/portal/appointments/a1/move-times?date=2026-03-13': { date: '2026-03-13', times: ['08:40', '11:20'] },
    });
    post.mockResolvedValue({ startsAt: '2026-03-13T11:20:00', practitioner: 'Dra. Arias', confirmationSent: true });
    show('/appointments/a1/move');

    expect(await screen.findByText('Turno de Benjamín Medina, ahora')).toBeInTheDocument();
    expect(screen.getByText('Jueves 12 de marzo, 10:00 · Dra. Arias · consulta')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: '11:00' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'vie 13' }));
    expect(address()).toBe('/appointments/a1/move?date=2026-03-13');
    await userEvent.click(await screen.findByRole('button', { name: '11:20' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar al Viernes 13 de marzo, 11:20' }));

    expect(post).toHaveBeenCalledWith('/api/portal/appointments/a1/move', { startsAt: '2026-03-13T11:20:00' });
    expect(await screen.findByRole('heading', { name: 'Turno cambiado' })).toBeInTheDocument();
    expect(screen.getByText(/Turno cambiado para Benjamín Medina: Viernes 13 de marzo, 11:20 con Dra. Arias/))
      .toHaveTextContent('El link del email anterior ya no sirve.');
  });

  it("says it may not be moved any more, and offers no day; and a refusal in the Thymeleaf screen's words", async () => {
    answer({ '/api/portal/appointments/a1': { ...TARGET, movable: false } });
    show('/appointments/a1/move');
    expect(await screen.findByText(/Este turno ya no se puede cambiar por la web/)).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Días' })).not.toBeInTheDocument();
  });

  it('says a time taken meanwhile, keeping the day chosen', async () => {
    answer({
      '/api/portal/appointments/a1': { ...TARGET, member: null },
      '/api/portal/appointments/a1/move-times?date=2026-03-12': { date: '2026-03-12', times: ['11:00'] },
    });
    post.mockRejectedValue(refusal('TIME_TAKEN'));
    show('/appointments/a1/move?date=2026-03-12');

    expect(await screen.findByText('Ahora')).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: '11:00' }));
    await userEvent.click(screen.getByRole('button', { name: /^Cambiar al/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Ese horario ya no está libre: elegí otro.');
    expect(screen.getByRole('button', { name: 'jue 12' })).toHaveAttribute('aria-pressed', 'true');
  });
});
