import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { AccountPage } from '../account/AccountPage';
import { api } from '../session';
import type { FamilyGroup } from './family';
import { FamilyGroupPage } from './FamilyGroupPage';

vi.mock('../session', () => ({ api: { get: vi.fn(), post: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

const GROUP: FamilyGroup = {
  id: 'g1', name: 'Familia Gómez', createdAt: '2026-03-01T10:00:00',
  members: [
    { patientId: 'p1', fullName: 'Marta Gómez', ageBand: 'ADULT', shares: true, requestedBy: null, relation: null, relationProved: true },
    { patientId: 'p2', fullName: 'Tomás Gómez', ageBand: 'ADOLESCENT', shares: false, requestedBy: null, relation: null, relationProved: true },
    { patientId: 'p3', fullName: 'Sofía Gómez', ageBand: 'CHILD', shares: true, requestedBy: 'p1', relation: 'MOTHER', relationProved: false },
  ],
};

function show(address: string) {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={[address]}>
          <Routes>
            <Route path="/family-groups/:id" element={<FamilyGroupPage />} />
            <Route path="/patients/:id/account" element={<AccountPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

const rowOf = (name: string) => screen.getByRole('link', { name }).closest('tr') as HTMLElement;

describe('a family group', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    post.mockResolvedValue(GROUP);
    get.mockImplementation(((path: string) => {
      if (path === '/api/family-groups/g1') return Promise.resolve(GROUP);
      if (path === '/api/family-groups/g1/changes') {
        return Promise.resolve([{ action: 'CHILD_ADDED', patientId: 'p3', patient: 'Sofía Gómez', at: '2026-03-02T09:00:00',
          by: 'recepcion' }]);
      }
      if (path === '/api/patients') return Promise.resolve([{ id: 'p9', fullName: 'Lía Gómez', document: null }]);
      return Promise.reject(new Error(path));
    }) as typeof api.get);
  });

  it('shows each member as their age says, and offers sharing only to whoever decides', async () => {
    show('/family-groups/g1');
    await screen.findByRole('heading', { name: 'Familia Gómez' });

    expect(within(rowOf('Marta Gómez')).getByText('adulto')).toBeInTheDocument();
    expect(within(rowOf('Tomás Gómez')).getByText('Turnos privados')).toBeInTheDocument();
    expect(within(rowOf('Sofía Gómez')).getByText('Siempre (menor de 13)')).toBeInTheDocument();
    expect(within(rowOf('Sofía Gómez')).getByText('A pedido de Marta Gómez (madre)')).toBeInTheDocument();
    expect(within(rowOf('Sofía Gómez')).getByText('Vínculo sin probar · pedido desde la web')).toBeInTheDocument();
    expect(within(rowOf('Sofía Gómez')).queryByRole('button', { name: /ompartir/ })).not.toBeInTheDocument();
    expect(screen.getByText('Agregado como menor — Sofía Gómez')).toBeInTheDocument();

    await userEvent.click(within(rowOf('Tomás Gómez')).getByRole('button', { name: 'Compartir' }));
    expect(post).toHaveBeenCalledWith('/api/family-groups/g1/members/p2/sharing', { shares: true });
    expect(await screen.findByRole('status')).toHaveTextContent('Ahora comparte sus turnos con el grupo.');
  });

  /** A relation is proved, and a child added, only with the documents seen (D-84, D-92). */
  it('proves a relation and adds a child only with the documents seen', async () => {
    show('/family-groups/g1');
    await screen.findByRole('heading', { name: 'Familia Gómez' });

    const prove = within(rowOf('Sofía Gómez')).getByRole('button', { name: 'Probar vínculo' });
    expect(prove).toBeDisabled();
    await userEvent.click(within(rowOf('Sofía Gómez')).getByLabelText('Vi lo que prueba el vínculo'));
    await userEvent.click(prove);
    expect(post).toHaveBeenLastCalledWith('/api/family-groups/g1/members/p3/relation-proof', { proofSeen: true });

    await userEvent.type(screen.getByLabelText('Buscar paciente'), 'Lía');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    const lia = (await screen.findByText('Lía Gómez')).closest('tr') as HTMLElement;
    expect(within(lia).getAllByRole('option', { name: /Gómez/ }).map((option) => option.textContent)).toEqual(['Marta Gómez']);
    await userEvent.selectOptions(within(lia).getByLabelText('que es su'), 'GUARDIAN');
    await userEvent.click(within(lia).getByLabelText('Vi la documentación'));
    await userEvent.click(within(lia).getByRole('button', { name: 'Agregar menor' }));
    expect(post).toHaveBeenLastCalledWith('/api/family-groups/g1/children',
      { patientId: 'p9', requestedBy: 'p1', relation: 'GUARDIAN', proofSeen: true });

    await userEvent.click(within(lia).getByLabelText('De 13 a 17: elige compartir'));
    await userEvent.click(within(lia).getByRole('button', { name: 'Entra al grupo' }));
    expect(post).toHaveBeenLastCalledWith('/api/family-groups/g1/members', { patientId: 'p9', shares: true });
  });

  it('says a refusal in Spanish', async () => {
    post.mockRejectedValue(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'A_CHILD' }));
    show('/family-groups/g1');
    await screen.findByRole('heading', { name: 'Familia Gómez' });

    await userEvent.type(screen.getByLabelText('Buscar paciente'), 'Lía');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Entra al grupo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Es menor de 13');
  });
});

describe("a patient's web account", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  function patientWith(account: unknown) {
    get.mockImplementation(((path: string) => {
      if (path === '/api/patients/p1') return Promise.resolve({ id: 'p1', fullName: 'Ana Pérez', document: null });
      if (path === '/api/patient-accounts') {
        return account ? Promise.resolve(account)
          : Promise.reject(new ApiError(404, { status: 404, error: 'Not found', message: 'x' }));
      }
      if (path.endsWith('/changes')) {
        return Promise.resolve([{ action: 'OPENED', at: '2026-03-01T10:00:00', by: 'recepcion' },
          { action: 'RECOVERY_SENT', at: '2026-03-02T10:00:00', by: null }]);
      }
      return Promise.reject(new Error(path));
    }) as typeof api.get);
  }

  it('opens one when there is none, saying the activation went out', async () => {
    patientWith(null);
    post.mockResolvedValue({ id: 'a1', patientId: 'p1', email: 'ana@mail.com', status: 'PENDING', openedAt: '', activationSent: true });
    show('/patients/p1/account');

    await userEvent.type(await screen.findByLabelText(/Email de la cuenta/), 'ana@mail.com');
    await userEvent.click(screen.getByRole('button', { name: 'Abrir cuenta' }));

    expect(post).toHaveBeenCalledWith('/api/patient-accounts', { patientId: 'p1', email: 'ana@mail.com' });
    expect(await screen.findByRole('status')).toHaveTextContent('Cuenta abierta: le enviamos el link de activación a ana@mail.com.');
  });

  it('offers what a pending one may do, and its history', async () => {
    patientWith({ id: 'a1', patientId: 'p1', email: 'ana@mail.com', status: 'PENDING', openedAt: '' });
    post.mockResolvedValue({ id: 'a1', patientId: 'p1', email: 'ana@mail.com', status: 'PENDING', openedAt: '', activationSent: true });
    show('/patients/p1/account');

    expect(await screen.findByText('Pendiente de activación')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reactivar' })).not.toBeInTheDocument();
    expect(await screen.findByText('pedido desde la web')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reenviar link de activación' }));
    expect(post).toHaveBeenCalledWith('/api/patient-accounts/a1/activation-resend', undefined);

    await userEvent.type(screen.getByLabelText(/Email nuevo/), 'otra@mail.com');
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar' }));
    expect(post).toHaveBeenLastCalledWith('/api/patient-accounts/a1/email-change', { email: 'otra@mail.com' });
  });
});
