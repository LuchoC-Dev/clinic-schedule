import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { api } from '../session';
import { BookingPage } from './BookingPage';

vi.mock('../session', () => ({ api: { get: vi.fn(), post: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

const ARIAS = { id: 'd1', fullName: 'Dra. Laura Arias', reasons: [
  { id: 'r1', name: 'consulta', durationMinutes: 30 }, { id: 'r2', name: 'control', durationMinutes: 20 }] };

function answers(path: string, query?: Record<string, unknown>) {
  if (path === '/api/practitioners') return Promise.resolve([ARIAS]);
  if (path === '/api/agenda/free-times') {
    return Promise.resolve(query?.date === '2026-03-11'
      ? { date: '2026-03-11', practitionerWorks: false, times: [] }
      : { date: '2026-03-10', practitionerWorks: true, times: ['09:00:00', '09:30:00'] });
  }
  if (path === '/api/patients') {
    return Promise.resolve([{ id: 'p1', fullName: 'Ana Pérez', document: { type: 'DNI', number: '30111222' } }]);
  }
  return Promise.reject(new Error(path));
}

function Agenda() {
  const location = useLocation();
  return <p>Agenda {location.search} {(location.state as { notice: string }).notice}</p>;
}

function show(address = '/agenda/new?practitionerId=d1&date=2026-03-10') {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={[address]}>
          <Routes>
            <Route path="/agenda/new" element={<BookingPage />} />
            <Route path="/agenda" element={<Agenda />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

describe('booking an appointment', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation(answers as typeof api.get);
  });

  it('books a patient found at a free time of the reason, and goes back to the agenda saying so', async () => {
    post.mockResolvedValue({});
    show();

    expect(await screen.findByRole('heading', { name: 'Nuevo turno con Dra. Laura Arias' })).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Razón'), 'r2');
    expect(get).toHaveBeenLastCalledWith('/api/agenda/free-times', { practitionerId: 'd1', reasonId: 'r2', date: '2026-03-10' });
    await userEvent.click(await screen.findByRole('radio', { name: '09:30' }));
    await userEvent.type(screen.getByLabelText('Buscar paciente'), 'Ana');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    await userEvent.click(await screen.findByRole('radio', { name: 'Ana Pérez — DNI 30111222' }));
    await userEvent.type(screen.getByLabelText('Nota (opcional)'), 'Trae estudios');
    await userEvent.click(screen.getByRole('button', { name: 'Reservar' }));

    expect(post).toHaveBeenCalledWith('/api/appointments', { patientId: 'p1', practitionerId: 'd1', reasonId: 'r2',
      startsAt: '2026-03-10T09:30:00', note: 'Trae estudios' });
    expect(await screen.findByText(/Agenda \?date=2026-03-10&practitionerId=d1 Turno reservado\./)).toBeInTheDocument();
  });

  /** Registered and booked at once (D-75), the phone saying whose it is (D-73). */
  it('books a new patient in the same step, at a time typed by hand', async () => {
    post.mockResolvedValue({});
    show();
    await screen.findByRole('heading', { name: 'Nuevo turno con Dra. Laura Arias' });

    await userEvent.click(screen.getByText('Paciente nuevo'));
    await userEvent.type(screen.getByLabelText('Nombre completo'), 'Rosa Nueva');
    await userEvent.selectOptions(screen.getByLabelText('Documento'), 'DNI');
    await userEvent.type(screen.getByLabelText('Número'), '40111222');
    await userEvent.type(screen.getByLabelText('Teléfono'), '1145556666');
    await userEvent.selectOptions(screen.getAllByLabelText('De quién es')[0], 'MOTHER');
    await userEvent.type(screen.getByLabelText('Nombre de esa persona'), 'Ana Madre');
    await userEvent.type(screen.getByLabelText('…u otra hora'), '11:15');
    await userEvent.click(screen.getByRole('button', { name: 'Reservar' }));

    expect(post).toHaveBeenCalledWith('/api/appointments/with-new-patient', {
      patient: { fullName: 'Rosa Nueva', documentType: 'DNI', documentNumber: '40111222',
        phone: { value: '1145556666', owner: { name: 'Ana Madre', relation: 'MOTHER' } }, email: null },
      practitionerId: 'd1', reasonId: 'r1', startsAt: '2026-03-10T11:15:00', note: null });
    expect(await screen.findByText(/Paciente registrado y turno reservado\./)).toBeInTheDocument();
  });

  it('says what is missing before sending anything', async () => {
    show();
    await screen.findByRole('heading', { name: 'Nuevo turno con Dra. Laura Arias' });

    await userEvent.click(screen.getByRole('button', { name: 'Reservar' }));

    expect(post).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('Elegí el paciente.');
  });

  it('says a day the practitioner does not work', async () => {
    show('/agenda/new?practitionerId=d1&date=2026-03-11');

    expect(await screen.findByText('Dra. Laura Arias no atiende ese día.')).toBeInTheDocument();
  });

  /** A refusal is said by its code, and the form keeps what was typed (D-52, D-100). */
  it('keeps the form after a refusal, said in Spanish', async () => {
    post.mockRejectedValue(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'DUPLICATE_DOCUMENT' }));
    show();
    await screen.findByRole('heading', { name: 'Nuevo turno con Dra. Laura Arias' });

    await userEvent.click(screen.getByText('Paciente nuevo'));
    await userEvent.type(screen.getByLabelText('Nombre completo'), 'Rosa Nueva');
    await userEvent.click(await screen.findByRole('radio', { name: '09:00' }));
    await userEvent.click(screen.getByRole('button', { name: 'Reservar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ese documento ya pertenece a otro paciente');
    expect(screen.getByLabelText('Nombre completo')).toHaveValue('Rosa Nueva');
    expect(within(screen.getByRole('group', { name: 'Horarios libres' })).getByRole('radio', { name: '09:00' })).toBeChecked();
  });
});
