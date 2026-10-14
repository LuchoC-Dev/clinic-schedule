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
import type { Agenda } from './agenda';
import { AgendaPage } from './AgendaPage';

vi.mock('../session', () => ({ api: { get: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);

const ARIAS = { id: 'd1', fullName: 'Dra. Laura Arias' };
const BENITEZ = { id: 'd2', fullName: 'Dr. Martín Benítez' };

function agenda(overrides: Partial<Agenda> = {}): Agenda {
  return {
    date: '2026-03-10',
    practitionerId: null,
    practitioners: [ARIAS, BENITEZ],
    appointments: [
      appointment({ id: 'a1', startsAt: '2026-03-10T09:00:00', arrivedAt: '2026-03-10T08:55:00', bookedVia: 'WEB' }),
      appointment({
        id: 'a2', startsAt: '2026-03-10T15:00:00', durationMinutes: 20, patient: 'Juan Gómez', patientDocument: null,
        practitionerId: 'd2', practitioner: 'Dr. Martín Benítez', reason: 'control', movedOnWeb: true,
        note: 'Trae estudios',
      }),
    ],
    ...overrides,
  };
}

function show(address = '/agenda') {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={[address]}>
          <Routes><Route path="/agenda" element={<AgendaPage />} /></Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

describe("reception's agenda of the day", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('shows each appointment of the day the server answered, with its state and what the web did', async () => {
    get.mockResolvedValue(agenda());
    show();

    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/agenda', { date: null, practitionerId: null });
    expect(screen.getByText('martes 10 de marzo')).toBeInTheDocument();
    expect(screen.getByText(/2 turnos · 1 llegó/)).toBeInTheDocument();

    const rows = screen.getAllByRole('row').slice(1);
    expect(within(rows[0]).getByText('09:00')).toBeInTheDocument();
    expect(within(rows[0]).getByText('DNI 30111222')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Llegó a las 08:55')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Reservado por la web')).toBeInTheDocument();
    expect(within(rows[0]).queryByText('↻ Movido por la web')).not.toBeInTheDocument();
    expect(within(rows[1]).getByText('15:00')).toBeInTheDocument();
    expect(within(rows[1]).getByText(/20′/)).toBeInTheDocument();
    expect(within(rows[1]).getByText('Reservado')).toBeInTheDocument();
    expect(within(rows[1]).queryByText('Reservado por la web')).not.toBeInTheDocument();
    expect(within(rows[1]).getByText('↻ Movido por la web')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Trae estudios')).toBeInTheDocument();
    expect(within(rows[1]).getByText('control')).toBeInTheDocument();
  });

  it('says an empty day so', async () => {
    get.mockResolvedValue(agenda({ appointments: [] }));
    show();

    expect(await screen.findByText('No hay turnos reservados para este día.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  /** The choice shown is whose agenda the server answered, never what the address asked (D-45). */
  it('marks the practitioner whose agenda it is, and asks for another one keeping the day', async () => {
    get.mockResolvedValue(agenda({ practitionerId: 'd2', appointments: [] }));
    show('/agenda?date=2026-03-10&practitionerId=d1');

    expect(await screen.findByRole('radio', { name: 'Dr. Martín Benítez' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Dra. Laura Arias' })).not.toBeChecked();
    expect(screen.getByRole('radio', { name: 'Todos' })).not.toBeChecked();

    await userEvent.click(screen.getByRole('radio', { name: 'Todos' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: '2026-03-10', practitionerId: null });
    await userEvent.click(screen.getByRole('radio', { name: 'Dra. Laura Arias' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: '2026-03-10', practitionerId: 'd1' });
  });

  it("moves from the server's day, and back to today, keeping the practitioner", async () => {
    get.mockResolvedValue(agenda({ practitionerId: 'd1', date: '2026-03-31' }));
    show('/agenda?practitionerId=d1');
    await screen.findByText('martes 31 de marzo');

    get.mockResolvedValue(agenda({ practitionerId: 'd1', date: '2026-04-01' }));
    await userEvent.click(screen.getByRole('button', { name: 'Día siguiente' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: '2026-04-01', practitionerId: 'd1' });
    await screen.findByText('miércoles 1 de abril');
    await userEvent.click(screen.getByRole('button', { name: 'Día anterior' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: '2026-03-31', practitionerId: 'd1' });

    await userEvent.click(screen.getByRole('button', { name: 'Hoy' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: null, practitionerId: 'd1' });
  });

  it('sends what it does not do yet to the screens, on the same day and practitioner', async () => {
    get.mockResolvedValue(agenda({ practitionerId: 'd1' }));
    show();

    expect(await screen.findByRole('link', { name: 'la agenda de siempre' }))
      .toHaveAttribute('href', '/agenda?date=2026-03-10&practitionerId=d1');
    expect(screen.getByRole('link', { name: 'Nuevo turno' }))
      .toHaveAttribute('href', '/agenda/new?date=2026-03-10&practitionerId=d1');
    expect(screen.getByRole('link', { name: 'Ana Pérez' })).toHaveAttribute('href', '/agenda/patients/p1');
  });

  it('says a refusal in Spanish', async () => {
    get.mockRejectedValue(new ApiError(403, { status: 403, error: 'Forbidden', message: 'You are not allowed to do this' }));
    show();

    expect(await screen.findByRole('alert')).toHaveTextContent('No tenés permiso para hacer esto.');
  });
});
