import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { api, logOut, useUser } from '../session';
import { PasswordPage } from './PasswordPage';

vi.mock('../session', () => ({ api: { post: vi.fn() }, logOut: vi.fn(), useUser: vi.fn() }));

const post = vi.mocked(api.post);

function show(temporaryPassword: boolean) {
  vi.mocked(useUser).mockReturnValue({ username: 'mesa', role: 'RECEPTION', practitionerId: null, temporaryPassword });
  render(<Theme><QueryClientProvider client={newQueryClient()}><PasswordPage /></QueryClientProvider></Theme>);
}

async function change(current: string, password: string, repeated: string) {
  await userEvent.type(screen.getByLabelText(/Contraseña actual/), current);
  await userEvent.type(screen.getByLabelText(/Contraseña nueva/), password);
  await userEvent.type(screen.getByLabelText(/Repetí la contraseña nueva/), repeated);
  await userEvent.click(screen.getByRole('button', { name: 'Cambiar' }));
}

describe("changing one's own password, every role's", () => {
  beforeEach(() => {
    post.mockReset();
    vi.mocked(logOut).mockReset();
  });

  it('says why when the password is temporary', () => {
    show(true);
    expect(screen.getByText(/Tu contraseña es temporal/)).toBeInTheDocument();
  });

  it('says nothing of it otherwise', () => {
    show(false);
    expect(screen.queryByText(/Tu contraseña es temporal/)).not.toBeInTheDocument();
  });

  it('sends nothing until the new one is typed the same twice', async () => {
    show(false);
    await change('la-de-antes-1', 'una-clave-nueva', 'otra-clave-nueva');
    expect(screen.getByRole('alert')).toHaveTextContent('La contraseña nueva y su repetición no coinciden.');
    expect(post).not.toHaveBeenCalled();
  });

  it("says each refusal in the Thymeleaf screen's words, keeping what was typed", async () => {
    const refused = (code: string) => new ApiError(400, { status: 400, error: 'Bad request', message: 'x', code });
    post.mockRejectedValueOnce(refused('WRONG_PASSWORD')).mockRejectedValueOnce(refused('UNCHANGED_PASSWORD'))
      .mockRejectedValueOnce(refused('ILLEGAL_ARGUMENT'));
    show(false);

    await change('la-de-antes-1', 'una-clave-nueva', 'una-clave-nueva');
    expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña actual no es correcta.');
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar' }));
    expect(await screen.findByText('La contraseña nueva tiene que ser distinta de la actual.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar' }));
    expect(await screen.findByText('La contraseña nueva lleva de 10 a 72 caracteres.')).toBeInTheDocument();
    expect(screen.getByLabelText(/Contraseña actual/)).toHaveValue('la-de-antes-1');
    expect(logOut).not.toHaveBeenCalled();
  });

  it('logs out once changed, for the login to say it', async () => {
    post.mockResolvedValue(undefined);
    show(true);

    await change('la-de-antes-1', 'una-clave-nueva', 'una-clave-nueva');

    expect(post).toHaveBeenCalledWith('/api/password', { currentPassword: 'la-de-antes-1', newPassword: 'una-clave-nueva' });
    await vi.waitFor(() => expect(logOut).toHaveBeenCalledWith(api, 'changed'));
  });
});
