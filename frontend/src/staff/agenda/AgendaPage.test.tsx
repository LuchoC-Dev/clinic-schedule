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

vi.mock('../session', () => ({ api: { get: vi.fn(), post: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

const ARIAS = { id: 'd1', fullName: 'Dra. Laura Arias' };
const BENITEZ = { id: 'd2', fullName: 'Dr. Martín Benítez' };

function agenda(overrides: Partial<Agenda> = {}): Agenda {
  const date = overrides.date ?? '2026-03-10';
  return {
    date,
    from: date,
    to: date,
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
          <Routes>
            <Route path="/agenda" element={<AgendaPage />} />
            <Route path="/agenda/new" element={<p>Pantalla de turno nuevo</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

/** A menu opens with a transition, which under a full build's load takes longer than the default second. */
const OPENING = { timeout: 5000 };

const rowOf = (name: string) => screen.getByText(name).closest('tr') as HTMLElement;

describe("reception's agenda", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it('shows each appointment the server answered, with its state and what the web did', async () => {
    get.mockResolvedValue(agenda());
    show();

    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/agenda', { date: null, practitionerId: null, week: null });
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
    expect(within(rows[0]).getByRole('link', { name: 'Ana Pérez' })).toHaveAttribute('href', '/patients/p1/appointments');
  });

  it('offers the arrival only to who has not arrived', async () => {
    get.mockResolvedValue(agenda());
    show();
    await screen.findByText('Ana Pérez');

    expect(within(rowOf('Ana Pérez')).queryByRole('button', { name: 'Llegó' })).not.toBeInTheDocument();
    expect(within(rowOf('Juan Gómez')).getByRole('button', { name: 'Llegó' })).toBeInTheDocument();
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

    await userEvent.click(screen.getByRole('radio', { name: 'Todos' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: '2026-03-10', practitionerId: null, week: null });
    await userEvent.click(screen.getByRole('radio', { name: 'Dra. Laura Arias' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: '2026-03-10', practitionerId: 'd1', week: null });
  });

  it("moves from the server's day, and back to today, keeping the practitioner", async () => {
    get.mockResolvedValue(agenda({ practitionerId: 'd1', date: '2026-03-31' }));
    show('/agenda?practitionerId=d1');
    await screen.findByText('martes 31 de marzo');

    get.mockResolvedValue(agenda({ practitionerId: 'd1', date: '2026-04-01' }));
    await userEvent.click(screen.getByRole('button', { name: 'Día siguiente' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: '2026-04-01', practitionerId: 'd1', week: null });
    await screen.findByText('miércoles 1 de abril');
    await userEvent.click(screen.getByRole('button', { name: 'Día anterior' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: '2026-03-31', practitionerId: 'd1', week: null });

    await userEvent.click(screen.getByRole('button', { name: 'Hoy' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: null, practitionerId: 'd1', week: null });
  });

  /** The week, Monday to Sunday, each day under its name; moving goes a week at a time (D-100). */
  it('shows a week day by day and moves a week at a time', async () => {
    get.mockResolvedValue(agenda({ date: '2026-03-11', from: '2026-03-09', to: '2026-03-15' }));
    show('/agenda?date=2026-03-11&view=week');

    expect(await screen.findByRole('heading', { name: 'Agenda de la semana' })).toBeInTheDocument();
    await screen.findByRole('heading', { name: 'lunes 9 de marzo' });
    expect(get).toHaveBeenCalledWith('/api/agenda', { date: '2026-03-11', practitionerId: null, week: 'true' });
    expect(screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)).toEqual([
      'lunes 9 de marzo', 'martes 10 de marzo', 'miércoles 11 de marzo', 'jueves 12 de marzo', 'viernes 13 de marzo',
      'sábado 14 de marzo', 'domingo 15 de marzo']);
    expect(screen.getAllByText('No hay turnos reservados para este día.')).toHaveLength(6);

    await userEvent.click(screen.getByRole('button', { name: 'Semana siguiente' }));
    expect(get).toHaveBeenLastCalledWith('/api/agenda', { date: '2026-03-18', practitionerId: null, week: 'true' });
  });

  it('books from the agenda with the day and the practitioner shown', async () => {
    get.mockResolvedValue(agenda({ practitionerId: 'd1' }));
    show();
    await screen.findByText('Ana Pérez');

    await userEvent.click(screen.getByRole('button', { name: 'Nuevo turno' }));
    expect(await screen.findByText('Pantalla de turno nuevo')).toBeInTheDocument();
  });

  it('records that the patient warned they will be late, and says so', async () => {
    get.mockResolvedValue(agenda());
    post.mockResolvedValue({});
    show();
    await screen.findByText('Juan Gómez');

    await userEvent.click(within(rowOf('Juan Gómez')).getByRole('button', { name: 'Más acciones de Juan Gómez' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Avisó demora' }, OPENING));

    expect(post).toHaveBeenCalledWith('/api/appointments/a2/late-arrival-notice');
    expect(await screen.findByRole('status')).toHaveTextContent('Anotado: se lo espera hasta el final del día.');
  });

  /** Who asked for it is required, the note is not (D-76); a refusal is said by its code (D-100). */
  it('cancels saying who asked, and says a refusal in Spanish', async () => {
    get.mockResolvedValue(agenda());
    post.mockRejectedValueOnce(new ApiError(409, { status: 409, error: 'Conflict', message: 'x',
      code: 'BOOKING_RULE_VIOLATION' }));
    show();
    await screen.findByText('Juan Gómez');

    await userEvent.click(within(rowOf('Juan Gómez')).getByRole('button', { name: 'Más acciones de Juan Gómez' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Cancelar' }, OPENING));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('button', { name: 'Cancelar turno' })).toBeDisabled();
    await userEvent.selectOptions(within(dialog).getByLabelText(/Lo pidió/), 'PATIENT');
    await userEvent.type(within(dialog).getByLabelText('Nota (opcional)'), 'Viaja');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar turno' }));

    expect(post).toHaveBeenCalledWith('/api/appointments/a2/cancel', { requestedBy: 'PATIENT', note: 'Viaja' });
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'No se puede: el turno ya fue cancelado, o el paciente ya había llegado o avisado su demora.');
    expect(within(dialog).getByLabelText('Nota (opcional)')).toHaveValue('Viaja');
  });

  /** The arrival is charged as it comes in; a very large amount is confirmed first (D-87, D-100). */
  it('charges the arrival, asking once more for a very large amount', async () => {
    get.mockResolvedValue(agenda());
    post.mockResolvedValue({});
    show();
    await screen.findByText('Juan Gómez');

    await userEvent.click(within(rowOf('Juan Gómez')).getByRole('button', { name: 'Llegó' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/Importe/), '1500000');
    await userEvent.selectOptions(within(dialog).getByLabelText('Medio de pago'), 'DEBIT');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Llegó' }));

    expect(post).not.toHaveBeenCalled();
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Vas a cobrar $ 1.500.000,00');
    // Another amount asks again, and the warning goes until it is sent.
    await userEvent.clear(within(dialog).getByLabelText(/Importe/));
    await userEvent.type(within(dialog).getByLabelText(/Importe/), '1600000');
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Llegó' }));
    expect(post).not.toHaveBeenCalled();
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Vas a cobrar $ 1.600.000,00');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sí, cobrar $ 1.600.000,00' }));
    expect(post).toHaveBeenCalledWith('/api/appointment-arrivals',
      { appointmentId: 'a2', amount: 1600000, means: 'DEBIT', coverage: undefined });
  });

  it('charges an ordinary amount at once, and a covered visit with its token', async () => {
    get.mockResolvedValue(agenda());
    post.mockResolvedValue({});
    show();
    await screen.findByText('Juan Gómez');

    await userEvent.click(within(rowOf('Juan Gómez')).getByRole('button', { name: 'Llegó' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByLabelText('Con cobertura'));
    await userEvent.type(within(dialog).getByLabelText(/Coseguro/), '2.500,50');
    await userEvent.type(within(dialog).getByLabelText('Token (si lo pide)'), 'T-123');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Llegó' }));

    expect(post).toHaveBeenCalledWith('/api/appointment-arrivals',
      { appointmentId: 'a2', amount: 2500.5, means: 'CASH', coverage: { token: 'T-123' } });
  });

  it('says a refusal in Spanish', async () => {
    get.mockRejectedValue(new ApiError(403, { status: 403, error: 'Forbidden', message: 'You are not allowed to do this' }));
    show();

    expect(await screen.findByRole('alert')).toHaveTextContent('No tenés permiso para hacer esto.');
  });
});
