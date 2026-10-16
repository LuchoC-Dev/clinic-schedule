import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { CancelLinkPage } from '../cancel/CancelLinkPage';
import { LoginPage } from '../LoginPage';
import { RegisterConfirmPage, RegisterPage } from '../register/RegisterPages';
import { api, SessionContext } from '../session';
import { ActivationPage, PasswordResetPage } from './LinkPages';
import { PasswordPage, PasswordRecoveryPage } from './PasswordPages';

vi.mock('../session', async (original) => ({
  ...(await original<typeof import('../session')>()),
  api: { get: vi.fn(), post: vi.fn() },
}));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

const refusal = (status: number, code?: string) =>
  new ApiError(status, { status, error: 'Refused', message: 'in English', code });

/** Where the screen went. */
function Address() {
  const location = useLocation();
  return <p data-testid="address">{location.pathname + location.search}</p>;
}

function show(address: string) {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <SessionContext value={{ loggedIn: false, documentTypes: ['DNI', 'PASAPORTE'] }}>
          <MemoryRouter initialEntries={[address]}>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/activation" element={<ActivationPage />} />
              <Route path="/password-reset" element={<PasswordResetPage />} />
              <Route path="/password-recovery" element={<PasswordRecoveryPage />} />
              <Route path="/password" element={<PasswordPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/register/sent" element={<p>enviado</p>} />
              <Route path="/register/confirm" element={<RegisterConfirmPage />} />
              <Route path="/cancel" element={<CancelLinkPage />} />
            </Routes>
            <Address />
          </MemoryRouter>
        </SessionContext>
      </QueryClientProvider>
    </Theme>,
  );
}

const address = () => screen.getByTestId('address').textContent;

async function typeNewPassword(password: string, repeated: string) {
  await userEvent.type(await screen.findByLabelText(/^Contraseña/), password);
  await userEvent.type(screen.getByLabelText(/^Repetila/), repeated);
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
});

describe('activating the account from its link', () => {
  it('says first that a link that no longer works is no use, without the form', async () => {
    get.mockRejectedValue(refusal(400, 'INVALID_ACTIVATION_LINK'));
    show('/activation?token=viejo');

    expect(await screen.findByRole('alert')).toHaveTextContent('Este link ya no sirve: venció o ya se usó');
    expect(get).toHaveBeenCalledWith('/api/portal/activation/viejo');
    expect(screen.queryByLabelText(/^Repetila/)).not.toBeInTheDocument();
  });

  it('asks nothing of a link without its token', () => {
    show('/activation');
    expect(screen.getByRole('alert')).toHaveTextContent('Este link ya no sirve');
    expect(get).not.toHaveBeenCalled();
  });

  it('sends the password typed the same twice, and goes to the login saying the account is active', async () => {
    get.mockResolvedValue(undefined);
    post.mockResolvedValue(undefined);
    show('/activation?token=abc');

    await typeNewPassword('una-clave-larga', 'otra-clave-larga');
    await userEvent.click(screen.getByRole('button', { name: 'Activar' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Las dos contraseñas no coinciden.');
    expect(post).not.toHaveBeenCalled();

    await userEvent.clear(screen.getByLabelText(/^Repetila/));
    await userEvent.type(screen.getByLabelText(/^Repetila/), 'una-clave-larga');
    await userEvent.click(screen.getByRole('button', { name: 'Activar' }));

    expect(await screen.findByText('Cuenta activada: ingresá con tu documento y tu contraseña.')).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith('/api/portal/activation', { token: 'abc', password: 'una-clave-larga' });
    expect(address()).toBe('/login?activated');
  });

  it("says a password's length, and a link used meanwhile as no use any more", async () => {
    get.mockResolvedValue(undefined);
    post.mockRejectedValueOnce(refusal(400, 'ILLEGAL_ARGUMENT'));
    show('/activation?token=abc');

    await typeNewPassword('una-clave-larga', 'una-clave-larga');
    await userEvent.click(screen.getByRole('button', { name: 'Activar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña tiene de 10 a 72 caracteres.');
    expect(screen.getByLabelText(/^Repetila/)).toHaveValue('una-clave-larga');

    post.mockRejectedValueOnce(refusal(400, 'INVALID_ACTIVATION_LINK'));
    await userEvent.click(screen.getByRole('button', { name: 'Activar' }));
    expect(await screen.findByText(/Este link ya no sirve: venció o ya se usó/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Repetila/)).not.toBeInTheDocument();
  });
});

describe('a new password', () => {
  it('is chosen from the recovery link, and the login says so', async () => {
    get.mockResolvedValue(undefined);
    post.mockResolvedValue(undefined);
    show('/password-reset?token=r1');

    await typeNewPassword('una-clave-nueva', 'una-clave-nueva');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('Tu contraseña quedó cambiada: ingresá con la nueva.')).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/portal/password-reset/r1');
    expect(post).toHaveBeenCalledWith('/api/portal/password-reset', { token: 'r1', password: 'una-clave-nueva' });
    expect(address()).toBe('/login?reset');
  });

  it('is asked for again from a link that is no use', async () => {
    get.mockRejectedValue(refusal(400, 'INVALID_RESET_LINK'));
    show('/password-reset?token=viejo');

    expect(await screen.findByRole('alert')).toHaveTextContent('ya se usó o pediste otro después');
    expect(screen.getByRole('link', { name: 'recuperar tu contraseña' })).toHaveAttribute('href', '/password-recovery');
  });

  it('is asked for with the document, and the answer is the same whatever it is', async () => {
    post.mockResolvedValue(undefined);
    show('/password-recovery');

    await userEvent.selectOptions(screen.getByLabelText('Tipo de documento'), 'PASAPORTE');
    await userEvent.type(screen.getByLabelText(/Número/), 'AB123');
    await userEvent.click(screen.getByRole('button', { name: 'Mandame el link' }));

    expect(post).toHaveBeenCalledWith('/api/portal/password-recovery', { documentType: 'PASAPORTE', documentNumber: 'AB123' });
    expect(await screen.findByRole('status')).toHaveTextContent('Si ese documento tiene una cuenta');
  });

  it('is changed by the patient logged in with the current one, the new one typed twice', async () => {
    post.mockRejectedValueOnce(refusal(400, 'WRONG_CURRENT')).mockResolvedValueOnce(undefined);
    show('/password');

    await userEvent.type(screen.getByLabelText(/Contraseña actual/), 'la-de-antes-1');
    await userEvent.type(screen.getByLabelText(/Contraseña nueva/), 'una-clave-nueva');
    await userEvent.type(screen.getByLabelText(/^Repetila/), 'otra-clave-nueva');
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Las dos contraseñas nuevas no coinciden.');
    expect(post).not.toHaveBeenCalled();

    await userEvent.clear(screen.getByLabelText(/^Repetila/));
    await userEvent.type(screen.getByLabelText(/^Repetila/), 'una-clave-nueva');
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña actual no es esa.');

    await userEvent.click(screen.getByRole('button', { name: 'Cambiar' }));
    expect(post).toHaveBeenLastCalledWith('/api/portal/password',
      { currentPassword: 'la-de-antes-1', newPassword: 'una-clave-nueva' });
    expect(await screen.findByRole('status')).toHaveTextContent('Tu contraseña quedó cambiada.');
    expect(screen.getByLabelText(/Contraseña actual/)).toHaveValue('');
  });
});

describe('registering', () => {
  it('sends the adult and the children typed, and says the email went out', async () => {
    post.mockResolvedValue(undefined);
    show('/register');

    await userEvent.type(screen.getAllByLabelText(/Nombre completo/)[0], 'Ana Pérez');
    await userEvent.type(screen.getAllByLabelText(/^Número/)[0], '30111222');
    await userEvent.type(screen.getAllByLabelText(/Fecha de nacimiento/)[0], '1990-05-14');
    await userEvent.type(screen.getByLabelText(/^Email/), 'ana@mail.com');
    await userEvent.type(screen.getByLabelText(/^Contraseña, de 10/), 'una-clave-larga');
    await userEvent.type(screen.getByLabelText(/^Repetila/), 'una-clave-larga');
    await userEvent.type(screen.getAllByLabelText(/Nombre completo/)[2], 'Sofía Pérez');
    await userEvent.type(screen.getAllByLabelText(/Fecha de nacimiento/)[2], '2020-01-02');
    await userEvent.selectOptions(screen.getAllByLabelText('Sos su')[1], 'GUARDIAN');
    await userEvent.click(screen.getByRole('button', { name: 'Registrarme' }));

    expect(post).toHaveBeenCalledWith('/api/portal/registrations', expect.objectContaining({
      fullName: 'Ana Pérez', documentNumber: '30111222', birthDate: '1990-05-14', email: 'ana@mail.com',
      phone: null, children: [{ fullName: 'Sofía Pérez', documentType: 'DNI', documentNumber: '',
        birthDate: '2020-01-02', relation: 'GUARDIAN' }],
    }));
    expect(await screen.findByText('enviado')).toBeInTheDocument();
    expect(address()).toBe('/register/sent');
  });

  it('sends nothing until the password is typed the same twice', async () => {
    show('/register');

    await userEvent.type(screen.getAllByLabelText(/Nombre completo/)[0], 'Ana Pérez');
    await userEvent.type(screen.getAllByLabelText(/^Número/)[0], '30111222');
    await userEvent.type(screen.getAllByLabelText(/Fecha de nacimiento/)[0], '1990-05-14');
    await userEvent.type(screen.getByLabelText(/^Email/), 'ana@mail.com');
    await userEvent.type(screen.getByLabelText(/^Contraseña, de 10/), 'una-clave-larga');
    await userEvent.type(screen.getByLabelText(/^Repetila/), 'otra-clave-larga');
    await userEvent.click(screen.getByRole('button', { name: 'Registrarme' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Las dos contraseñas no coinciden.');
    expect(post).not.toHaveBeenCalled();
  });

  it('keeps what was typed on a refusal', async () => {
    post.mockRejectedValue(refusal(409, 'UNDER_EIGHTEEN'));
    show('/register');

    await userEvent.type(screen.getAllByLabelText(/Nombre completo/)[0], 'Ana Pérez');
    await userEvent.type(screen.getAllByLabelText(/^Número/)[0], '30111222');
    await userEvent.type(screen.getAllByLabelText(/Fecha de nacimiento/)[0], '2012-05-14');
    await userEvent.type(screen.getByLabelText(/^Email/), 'ana@mail.com');
    await userEvent.type(screen.getByLabelText(/^Contraseña, de 10/), 'una-clave-larga');
    await userEvent.type(screen.getByLabelText(/^Repetila/), 'una-clave-larga');
    await userEvent.click(screen.getByRole('button', { name: 'Registrarme' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Desde la web se registran mayores de 18 años');
    expect(screen.getAllByLabelText(/Nombre completo/)[0]).toHaveValue('Ana Pérez');
    expect(address()).toBe('/register');
  });

  it('is confirmed with the button, and the login names the children not registered', async () => {
    get.mockResolvedValue(undefined);
    post.mockResolvedValue({ notRegistered: ['Tomás Pérez'] });
    show('/register/confirm?token=c1');

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar registro' }));

    expect(await screen.findByText('Registro confirmado: ingresá con tu documento y tu contraseña.')).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/portal/registrations/confirmation/c1');
    expect(post).toHaveBeenCalledWith('/api/portal/registrations/confirmation', { token: 'c1' });
    expect(address()).toBe('/login?registered');
    expect(screen.getByText(/No pudimos registrar a Tomás Pérez con ese documento/)).toBeInTheDocument();
  });

  it('sends to reception a document someone registered meanwhile', async () => {
    get.mockResolvedValue(undefined);
    post.mockRejectedValue(refusal(409));
    show('/register/confirm?token=c1');

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar registro' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Acercate a recepción con tu documento');
    expect(screen.queryByRole('button', { name: 'Confirmar registro' })).not.toBeInTheDocument();
  });

  it('says a confirmation link that is no use', async () => {
    get.mockRejectedValue(refusal(400, 'INVALID_REGISTRATION_LINK'));
    show('/register/confirm?token=viejo');
    expect(await screen.findByRole('alert')).toHaveTextContent('Si todavía no te registraste, registrate de nuevo.');
  });
});

describe("cancelling from an email's link", () => {
  it('shows when and with whom, and cancels it', async () => {
    get.mockResolvedValue({ startsAt: '2026-03-12T10:00:00', practitioner: 'Dra. Arias' });
    post.mockResolvedValue(undefined);
    show('/cancel?token=t1');

    expect(await screen.findByText(/Turno del/)).toHaveTextContent('Turno del Jueves 12 de marzo, 10:00 con Dra. Arias.');
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar el turno' }));

    expect(get).toHaveBeenCalledWith('/api/portal/cancel-links/t1');
    expect(post).toHaveBeenCalledWith('/api/portal/cancel-links/t1/cancel');
    expect(await screen.findByRole('status')).toHaveTextContent('Turno cancelado.');
    expect(screen.queryByRole('button', { name: 'Cancelar el turno' })).not.toBeInTheDocument();
  });

  it("says why it is no use, in the Thymeleaf screen's words", async () => {
    get.mockRejectedValue(refusal(409, 'TOO_LATE'));
    show('/cancel?token=t1');
    expect(await screen.findByRole('alert')).toHaveTextContent('Falta menos de un día para el turno');
  });

  it('says a cancellation refused once asked', async () => {
    get.mockResolvedValue({ startsAt: '2026-03-12T10:00:00', practitioner: 'Dra. Arias' });
    post.mockRejectedValue(refusal(404, 'GONE'));
    show('/cancel?token=t1');

    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar el turno' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('el turno se movió o ya se canceló');
  });
});
