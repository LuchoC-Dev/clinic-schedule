import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { api } from '../session';
import type { Readings, User } from './admin';
import { CashDeskLogPage, ReadingsPage } from './LogPages';
import { UserPage } from './UserPage';
import { UsersPage } from './UsersPage';

vi.mock('../session', () => ({ api: { get: vi.fn(), post: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

const PRACTITIONERS = [{ id: 'p1', fullName: 'Dra. Laura Arias', reasons: [] }, { id: 'p2', fullName: 'Dr. Benítez', reasons: [] }];
const ARIAS: User = { id: 'u1', username: 'arias', role: 'PRACTITIONER', practitionerId: 'p1', active: true };
const MESA: User = { id: 'u2', username: 'mesa', role: 'RECEPTION', practitionerId: null, active: false };

/** Where the screen went, and what it searched for. */
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
            <Route path="/users" element={<UsersPage />} />
            <Route path="/users/:id" element={<UserPage />} />
            <Route path="/record-readings" element={<ReadingsPage />} />
            <Route path="/cash-desk-log" element={<CashDeskLogPage />} />
          </Routes>
          <Address />
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

function answer(paths: Record<string, unknown>) {
  get.mockImplementation(((path: string) => (path in paths ? Promise.resolve(paths[path]) : Promise.reject(new Error(path)))) as typeof api.get);
}

const rowOf = (name: string) => screen.getByRole('link', { name }).closest('tr') as HTMLElement;

describe('the users', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    answer({
      '/api/users': [ARIAS, MESA],
      '/api/practitioners': PRACTITIONERS,
      '/api/users/practitioners-without-user': [{ id: 'p2', fullName: 'Dr. Benítez' }],
      '/api/users/u3': { id: 'u3', username: 'benitez', role: 'PRACTITIONER', practitionerId: 'p2', active: true },
      '/api/users/u3/changes': [],
    });
  });

  it('lists every user with their role, practitioner and state', async () => {
    show('/users');
    await screen.findByRole('link', { name: 'arias' });

    expect(within(rowOf('arias')).getByText('Profesional')).toBeInTheDocument();
    expect(within(rowOf('arias')).getByText('Dra. Laura Arias')).toBeInTheDocument();
    expect(within(rowOf('arias')).getByText('Activo')).toBeInTheDocument();
    expect(within(rowOf('mesa')).getByText('Recepción')).toBeInTheDocument();
    expect(within(rowOf('mesa')).getByText('Desactivado')).toBeInTheDocument();
  });

  /** A practitioner's user stands for one with no active user, chosen among those alone (D-68). */
  it('asks for the practitioner only for a practitioner, offering those without a user, and goes to the new user', async () => {
    post.mockResolvedValue({ id: 'u3', username: 'benitez', role: 'PRACTITIONER', practitionerId: 'p2', active: true });
    show('/users');
    await screen.findByRole('link', { name: 'arias' });
    expect(screen.queryByLabelText(/^Profesional/)).not.toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/^Usuario/), 'benitez');
    await userEvent.selectOptions(screen.getByLabelText(/^Rol/), 'PRACTITIONER');
    const practitioner = screen.getByLabelText(/^Profesional/);
    expect(within(practitioner).getAllByRole('option').map((option) => option.textContent)).toEqual(['— elegí —', 'Dr. Benítez']);
    await userEvent.selectOptions(practitioner, 'p2');
    await userEvent.type(screen.getByLabelText(/^Contraseña/), 'una-clave-larga');
    await userEvent.click(screen.getByRole('button', { name: 'Crear' }));

    expect(post).toHaveBeenCalledWith('/api/users',
      { username: 'benitez', role: 'PRACTITIONER', practitionerId: 'p2', password: 'una-clave-larga' });
    expect(await screen.findByRole('status')).toHaveTextContent('Usuario creado: benitez.');
    expect(screen.getByTestId('address')).toHaveTextContent('/users/u3');
  });

  it('keeps what was typed when the username was used before', async () => {
    post.mockRejectedValue(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'USERNAME_TAKEN' }));
    show('/users');
    await screen.findByRole('link', { name: 'arias' });

    await userEvent.type(screen.getByLabelText(/^Usuario/), 'arias');
    await userEvent.selectOptions(screen.getByLabelText(/^Rol/), 'ADMIN');
    await userEvent.type(screen.getByLabelText(/^Contraseña/), 'una-clave-larga');
    await userEvent.click(screen.getByRole('button', { name: 'Crear' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/^Ese nombre de usuario ya se usó/);
    expect(screen.getByLabelText(/^Usuario/)).toHaveValue('arias');
    expect(screen.getByLabelText(/^Contraseña/)).toHaveValue('una-clave-larga');
  });
});

describe('a user', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    answer({
      '/api/users/u1': ARIAS,
      '/api/users/u2': MESA,
      '/api/practitioners': PRACTITIONERS,
      '/api/users/u1/changes': [
        { action: 'PASSWORD_RESET', changedBy: 'admin', changedAt: '2026-03-12T09:00:00' },
        { action: 'CREATED', changedBy: 'admin', changedAt: '2026-03-10T08:30:00' },
      ],
      '/api/users/u2/changes': [],
    });
  });

  it('says who they are and what was done to them, and offers deactivating an active one', async () => {
    post.mockResolvedValue({ ...ARIAS, active: false });
    show('/users/u1');

    expect(await screen.findByText('Profesional · Dra. Laura Arias · Activo')).toBeInTheDocument();
    expect(screen.getByText('Blanqueó la contraseña')).toBeInTheDocument();
    expect(screen.getByText('jue 12/03/2026 09:00')).toBeInTheDocument();
    expect(screen.getByText('Creó el usuario')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reactivar' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Desactivar' }));
    expect(post).toHaveBeenCalledWith('/api/users/u1/deactivate');
    expect(await screen.findByRole('status')).toHaveTextContent('Usuario desactivado: ya no puede ingresar.');
  });

  it('offers reactivating one deactivated, and nothing done to them yet', async () => {
    post.mockResolvedValue({ ...MESA, active: true });
    show('/users/u2');

    expect(await screen.findByText('Recepción · Desactivado')).toBeInTheDocument();
    expect(screen.getByText('Sin acciones registradas.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Desactivar' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reactivar' }));
    expect(post).toHaveBeenCalledWith('/api/users/u2/reactivate');
    expect(await screen.findByRole('status')).toHaveTextContent('Usuario reactivado.');
  });

  it('resets the password to the one typed, and clears it once done', async () => {
    post.mockResolvedValue(ARIAS);
    show('/users/u1');
    await screen.findByText('Creó el usuario');

    await userEvent.type(screen.getByLabelText(/^Contraseña nueva/), 'otra-clave-larga');
    await userEvent.click(screen.getByRole('button', { name: 'Blanquear' }));

    expect(post).toHaveBeenCalledWith('/api/users/u1/password-reset', { password: 'otra-clave-larga' });
    expect(await screen.findByRole('status')).toHaveTextContent('Contraseña blanqueada. Pasásela al usuario.');
    expect(screen.getByLabelText(/^Contraseña nueva/)).toHaveValue('');
  });

  it('says why an admin may not deactivate themselves', async () => {
    post.mockRejectedValue(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'SELF_DEACTIVATION' }));
    show('/users/u1');
    await userEvent.click(await screen.findByRole('button', { name: 'Desactivar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No podés desactivar tu propio usuario.');
  });
});

const READINGS: Readings = {
  from: '2026-02-12', to: '2026-03-12', limitReached: true,
  readings: [
    { readAt: '2026-03-12T10:15:00', practitioner: { id: 'p1', fullName: 'Dra. Laura Arias' }, readBy: 'arias',
      patient: { id: 'a', fullName: 'Ana Pérez', document: 'DNI 30111222' }, access: 'VISIT_TODAY', attachment: true, copy: false },
    { readAt: '2026-03-11T09:00:00', practitioner: null, readBy: null,
      patient: { id: 'a', fullName: 'Ana Pérez', document: null }, access: 'COPY_REQUEST', attachment: false, copy: true },
  ],
};

describe("the admins' logs", () => {
  beforeEach(() => {
    get.mockReset();
    get.mockImplementation(((path: string) => {
      if (path === '/api/practitioners') return Promise.resolve(PRACTITIONERS);
      if (path === '/api/record-readings') return Promise.resolve(READINGS);
      if (path === '/api/cash-desk-log') {
        return Promise.resolve({
          date: '2026-03-12',
          movements: [
            { kind: 'CHARGE', amount: 15000, means: 'CASH', recordedBy: 'recepcion', recordedAt: '2026-03-12T10:15:00' },
            { kind: 'REFUND', amount: 5000, means: 'DEBIT', recordedBy: 'mesa', recordedAt: '2026-03-12T11:00:00' },
          ],
          total: { charged: { count: 1, amount: 15000 }, refunded: { count: 1, amount: 5000 }, left: 10000, byMeans: [], drawer: 15000 },
          byRecorder: [],
        });
      }
      return Promise.reject(new Error(path));
    }) as typeof api.get);
  });

  it('shows each reading in words, says there are more, and searches what is typed from the address', async () => {
    show('/record-readings?practitionerId=p1');
    const first = (await screen.findByText('jue 12/03/2026 10:15')).closest('tr') as HTMLElement;
    expect(get).toHaveBeenCalledWith('/api/record-readings', { patient: null, practitionerId: 'p1', from: null, to: null });

    expect(within(first).getByText('Ana Pérez — DNI 30111222')).toBeInTheDocument();
    expect(within(first).getByText('Lo atiende hoy')).toBeInTheDocument();
    expect(within(first).getByText('Archivo adjunto')).toBeInTheDocument();
    const second = screen.getByText('mié 11/03/2026 09:00').closest('tr') as HTMLElement;
    expect(within(second).getByText('sin usuario registrado')).toBeInTheDocument();
    expect(within(second).getByText('Copia pedida por el paciente')).toBeInTheDocument();
    expect(within(second).getByText('Copia de la historia')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Hay más lecturas de las que se muestran: acotá la búsqueda');
    expect(screen.getByLabelText('Desde')).toHaveValue('2026-02-12');
    expect(await screen.findByRole('option', { name: 'Dra. Laura Arias' })).toBeInTheDocument();
    expect(screen.getByLabelText('Profesional')).toHaveValue('p1');

    await userEvent.type(screen.getByLabelText(/^Paciente/), ' Pérez ');
    await userEvent.selectOptions(screen.getByLabelText('Profesional'), '');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(screen.getByTestId('address')).toHaveTextContent('/record-readings?patient=P%C3%A9rez');
    expect(get).toHaveBeenLastCalledWith('/api/record-readings', { patient: 'Pérez', practitionerId: null, from: null, to: null });
  });

  it('says two days the wrong way round', async () => {
    get.mockImplementation(((path: string) => (path === '/api/record-readings'
      ? Promise.reject(new ApiError(400, { status: 400, error: 'Bad request', message: 'x', code: 'ILLEGAL_ARGUMENT' }))
      : Promise.resolve(PRACTITIONERS))) as typeof api.get);
    show('/record-readings?from=2026-03-12&to=2026-03-01');
    expect(await screen.findByRole('alert')).toHaveTextContent('El primer día es posterior al último.');
  });

  it("shows the cash desk's day without its patients, a refund with its minus", async () => {
    show('/cash-desk-log');
    const refund = (await screen.findByText('Devolución')).closest('tr') as HTMLElement;
    expect(within(refund).getByText('−$ 5.000,00')).toBeInTheDocument();
    expect(within(refund).getByText('Débito')).toBeInTheDocument();
    expect(within(refund).getByText('mesa')).toBeInTheDocument();
    const charge = screen.getByText('Cobro').closest('tr') as HTMLElement;
    expect(within(charge).getByText('$ 15.000,00')).toBeInTheDocument();
    expect(screen.getByText('Efectivo en caja')).toBeInTheDocument();
  });
});
