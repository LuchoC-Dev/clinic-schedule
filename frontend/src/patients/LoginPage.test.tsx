import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../shared/api';
import { Theme } from '../shared/theme';
import { LOGIN_FAILED, LoginPage } from './LoginPage';
import { SessionContext, SetSessionContext, logIn } from './session';

vi.mock('./session', async (original) => ({
  ...(await original<typeof import('./session')>()),
  logIn: vi.fn(),
}));

function show(setSession = vi.fn()) {
  render(
    <Theme>
      <SessionContext value={{ loggedIn: false, documentTypes: ['DNI', 'PASAPORTE'] }}>
        <SetSessionContext value={setSession}>
          <MemoryRouter initialEntries={['/login']}><LoginPage /></MemoryRouter>
        </SetSessionContext>
      </SessionContext>
    </Theme>,
  );
  return setSession;
}

async function logInWith(type: string, number: string, password: string) {
  await userEvent.selectOptions(screen.getByLabelText('Tipo de documento'), type);
  await userEvent.type(screen.getByLabelText(/Número/), number);
  await userEvent.type(screen.getByLabelText(/Contraseña/), password);
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
}

describe("the patients' login", () => {
  beforeEach(() => {
    vi.mocked(logIn).mockReset();
  });

  it('logs in with the type of document chosen and the number typed apart', async () => {
    const session = { loggedIn: true, documentTypes: ['DNI', 'PASAPORTE'] };
    vi.mocked(logIn).mockResolvedValue(session);
    const setSession = show();

    await logInWith('PASAPORTE', 'AB123456', 'una-clave-larga');

    expect(logIn).toHaveBeenCalledWith('PASAPORTE', 'AB123456', 'una-clave-larga');
    expect(setSession).toHaveBeenCalledWith(session);
  });

  /** Saying that the account is locked would tell that the document has one (D-86). */
  it('says the same of every failure, keeping the document typed', async () => {
    vi.mocked(logIn).mockRejectedValue(new ApiError(401, { status: 401, error: 'Unauthenticated',
      message: 'Wrong document or password, or the account is locked for a while' }));
    show();

    await logInWith('DNI', '30111222', 'no-es');

    expect(await screen.findByRole('alert')).toHaveTextContent(LOGIN_FAILED);
    expect(LOGIN_FAILED).toContain('se bloquea 15 minutos');
    expect(screen.getByLabelText(/Número/)).toHaveValue('30111222');
    expect(screen.getByLabelText(/Contraseña/)).toHaveValue('');
  });

  it('offers recovering the password and registering, on their screens', () => {
    show();
    expect(screen.getByRole('link', { name: '¿Olvidaste tu contraseña?' }))
      .toHaveAttribute('href', '/password-recovery');
    expect(screen.getByRole('link', { name: 'Registrate' })).toHaveAttribute('href', '/register');
  });
});
