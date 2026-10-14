import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { api } from '../session';
import type { Patient } from '../types';
import { PatientPage } from './PatientPage';
import { PatientsPage } from './PatientsPage';

vi.mock('../session', () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const put = vi.mocked(api.put);

const ANA: Patient = {
  id: 'p1', fullName: 'Ana Pérez', document: { type: 'DNI', number: '30111222' }, identity: 'VERIFIED', alias: null,
  birthDate: '1990-05-14', registeredVia: 'RECEPTION',
  phone: { number: '1145556666', owner: { name: 'Luis Pérez', relation: 'FATHER' }, consent: 'OWNER' },
  email: null, insurance: null,
};

function show(address: string) {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={[address]}>
          <Routes>
            <Route path="/patients" element={<PatientsPage />} />
            <Route path="/patients/:id" element={<PatientPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

describe('the patients', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    put.mockReset();
  });

  it('finds them and registers one, the document seen and with their details', async () => {
    get.mockResolvedValue([{ ...ANA, identity: 'DECLARED', alias: 'NN-4821' }]);
    post.mockResolvedValue({ ...ANA, id: 'p9', fullName: 'Rosa Nueva' });
    show('/patients?query=ana');

    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/patients', { query: 'ana' });
    expect(screen.getByText('(NN-4821)')).toBeInTheDocument();
    expect(screen.getByText('No verificada')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver turnos' })).toHaveAttribute('href', '/patients/p1/appointments');

    await userEvent.type(screen.getByLabelText(/Nombre completo/), 'Rosa Nueva');
    await userEvent.selectOptions(screen.getByLabelText('Documento'), 'DNI');
    await userEvent.type(screen.getByLabelText('Número'), '40111222');
    await userEvent.click(screen.getByLabelText('Vi el documento'));
    await userEvent.type(screen.getByLabelText('Obra social o prepaga'), 'OSDE');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar' }));

    expect(post).toHaveBeenCalledWith('/api/patients', { fullName: 'Rosa Nueva', documentType: 'DNI',
      documentNumber: '40111222', documentSeen: true, birthDate: null, phone: null, email: null,
      insurance: { insurer: 'OSDE', memberNumber: null, plan: null } });
  });

  it('says when no patient matches', async () => {
    get.mockResolvedValue([]);
    show('/patients?query=nadie');

    expect(await screen.findByText('No hay pacientes que coincidan.')).toBeInTheDocument();
  });

  /** Nobody is turned away for lack of a name (D-60). */
  it('registers one who gives no name', async () => {
    post.mockResolvedValue({ ...ANA, id: 'p9', fullName: 'NN-4821', alias: 'NN-4821' });
    get.mockResolvedValue({ ...ANA, id: 'p9', fullName: 'NN-4821', alias: 'NN-4821', identity: 'ALIAS' });
    show('/patients');

    await userEvent.click(screen.getByRole('button', { name: 'Registrar sin datos (NN)' }));

    expect(post).toHaveBeenCalledWith('/api/patients/unidentified');
    expect(await screen.findByRole('status')).toHaveTextContent('Paciente registrado sin datos: NN-4821.');
  });
});

describe("a patient's screen", () => {
  beforeEach(() => {
    get.mockReset();
    put.mockReset();
    get.mockImplementation(((path: string) => {
      if (path === '/api/patients/p1') return Promise.resolve(ANA);
      if (path.endsWith('/identity-changes')) {
        return Promise.resolve([{ changedAt: '2026-09-25T10:00:00', changedBy: 'recepcion',
          before: { fullName: 'NN-4821', document: null, identity: 'ALIAS' },
          after: { fullName: 'Ana Pérez', document: { type: 'DNI', number: '30111222' }, identity: 'VERIFIED' } }]);
      }
      return Promise.resolve([]);
    }) as typeof api.get);
  });

  it('shows the identity, its history, and changes it with the document seen', async () => {
    put.mockResolvedValue({});
    show('/patients/p1');

    expect(await screen.findByRole('heading', { name: 'Ana Pérez' })).toBeInTheDocument();
    expect(screen.getByText(/Identidad: Verificada/)).toBeInTheDocument();
    expect(await screen.findByText('NN-4821, sin documento — Sin datos')).toBeInTheDocument();
    expect(screen.getByText('Ana Pérez, DNI 30111222 — Verificada')).toBeInTheDocument();
    expect(screen.getByText('Sin cambios desde que se registró.')).toBeInTheDocument();

    await userEvent.clear(screen.getAllByLabelText(/Nombre completo/)[0]);
    await userEvent.type(screen.getAllByLabelText(/Nombre completo/)[0], 'Ana María Pérez');
    await userEvent.click(screen.getByLabelText('Vi el documento'));
    await userEvent.click(screen.getAllByRole('button', { name: 'Guardar' })[0]);

    expect(put).toHaveBeenCalledWith('/api/patients/p1', { fullName: 'Ana María Pérez', documentType: 'DNI',
      documentNumber: '30111222', documentSeen: true });
    expect(await screen.findByText('Datos guardados.')).toBeInTheDocument();
  });

  /** The consent of someone else's phone may be theirs, named (D-77). */
  it("offers the phone's owner as who agreed, and saves the consent", async () => {
    put.mockResolvedValue({});
    show('/patients/p1');

    const phone = await screen.findByLabelText('Al teléfono');
    expect(phone).toHaveValue('OWNER');
    expect(screen.getByRole('option', { name: 'Lo dio Padre: Luis Pérez' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Al email')).not.toBeInTheDocument();
    await userEvent.selectOptions(phone, 'PATIENT');
    await userEvent.click(screen.getAllByRole('button', { name: 'Guardar' })[2]);

    expect(put).toHaveBeenCalledWith('/api/patients/p1/contact-consent', { phone: 'PATIENT', email: null });
  });

  it('says a refusal of the identity in Spanish', async () => {
    put.mockRejectedValue(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'VERIFICATION_REQUIRED' }));
    show('/patients/p1');

    await userEvent.click((await screen.findAllByRole('button', { name: 'Guardar' }))[0]);

    expect(await screen.findByRole('alert')).toHaveTextContent('El paciente tiene cuenta web');
  });
});
