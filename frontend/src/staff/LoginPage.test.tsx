import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, NetworkError } from '../shared/api';
import { Theme } from '../shared/theme';
import { LoginPage } from './LoginPage';
import type { StaffUser } from './navigation';
import { SetUserContext, logIn } from './session';

vi.mock('./session', async (original) => ({
  ...(await original<typeof import('./session')>()),
  logIn: vi.fn(),
}));

const reception: StaffUser = { username: 'recepcion', role: 'RECEPTION', practitionerId: null, temporaryPassword: false };

function show(setUser = vi.fn(), address = '/login') {
  render(
    <Theme>
      <SetUserContext value={setUser}>
        <MemoryRouter initialEntries={[address]}><LoginPage /></MemoryRouter>
      </SetUserContext>
    </Theme>,
  );
  return setUser;
}

async function logInAs(username: string, password: string) {
  await userEvent.type(screen.getByLabelText(/Usuario/), username);
  await userEvent.type(screen.getByLabelText(/Contraseña/), password);
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
}

describe("the staff's login", () => {
  beforeEach(() => {
    vi.mocked(logIn).mockReset();
  });

  it('logs in and hands the user to the application', async () => {
    vi.mocked(logIn).mockResolvedValue(reception);
    const setUser = show();

    await logInAs('recepcion', 'recepcion123');

    expect(logIn).toHaveBeenCalledWith('recepcion', 'recepcion123');
    expect(setUser).toHaveBeenCalledWith(reception);
  });

  it('says a wrong one in Spanish, keeping the username and clearing the password', async () => {
    vi.mocked(logIn).mockRejectedValue(new ApiError(401,
      { status: 401, error: 'Unauthenticated', message: 'Wrong username or password' }));
    const setUser = show();

    await logInAs('recepcion', 'no-es');

    expect(await screen.findByRole('alert')).toHaveTextContent('Usuario o contraseña incorrectos.');
    expect(screen.getByLabelText(/Usuario/)).toHaveValue('recepcion');
    expect(screen.getByLabelText(/Contraseña/)).toHaveValue('');
    expect(setUser).not.toHaveBeenCalled();
  });

  it('says when the server could not be reached', async () => {
    vi.mocked(logIn).mockRejectedValue(new NetworkError(new TypeError('down')));
    show();

    await logInAs('recepcion', 'recepcion123');

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar con el servidor');
  });

  it('says how the last session ended', () => {
    show(vi.fn(), '/login?expired');
    expect(screen.getByText(/Tu sesión se cerró/)).toBeInTheDocument();
  });
});
