import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { api } from '../session';
import type { MyOrder, PendingCheck } from './lists';
import { MyOrdersPage } from './MyOrdersPage';
import { PendingChecksPage } from './PendingChecksPage';

vi.mock('../session', () => ({ api: { get: vi.fn(), post: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

function pending(overrides: Partial<PendingCheck> = {}): PendingCheck {
  return {
    checkId: 'k1', patientId: 'p1', patientName: 'Ana Pérez', patientDocument: 'DNI 30111222',
    description: 'Extracción de sangre', performedAt: '2026-03-10T10:15:00', performedByName: 'Dra. Arias',
    mayRead: false, ...overrides,
  };
}

function answering(answers: Record<string, unknown>) {
  get.mockImplementation(((path: string) => (path in answers
    ? Promise.resolve(answers[path]) : Promise.reject(new Error(path)))) as typeof api.get);
}

/** Where the screen went, and what it said on going. */
function Arrived() {
  const location = useLocation();
  return <p>{location.pathname}{location.search} · {(location.state as { notice?: string } | null)?.notice}</p>;
}

function show(path: string) {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/checks/pending" element={<PendingChecksPage />} />
            <Route path="/orders/mine" element={<MyOrdersPage />} />
            <Route path="/clinical-records/:id" element={<Arrived />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

describe('the pending checks', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it('leads to a record that may be read, and asks for access to one that may not, with the reason optional or not', async () => {
    answering({
      '/api/checks/pending': [pending(), pending({ checkId: 'k2', patientId: 'p2', patientName: 'Juan Gómez',
        patientDocument: null, mayRead: true })],
      '/api/access-grants/settings': { reasonRequired: false },
    });
    show('/checks/pending');

    const mine = (await screen.findByText('Juan Gómez')).closest('tr') as HTMLElement;
    expect(within(mine).getByRole('button', { name: 'Ver historia' })).toBeVisible();
    const theirs = screen.getByText('Ana Pérez — DNI 30111222').closest('tr') as HTMLElement;
    expect(within(theirs).queryByRole('button', { name: 'Ver historia' })).toBeNull();
    expect(await within(theirs).findByLabelText('Motivo (opcional)')).toBeVisible();
  });

  it('asks for access with its reason and, granted, goes to the record saying until when', async () => {
    answering({ '/api/checks/pending': [pending()], '/api/access-grants/settings': { reasonRequired: true } });
    post.mockRejectedValueOnce(new ApiError(400, { status: 400, error: 'x', message: 'x', code: 'REASON_REQUIRED' }));
    post.mockResolvedValueOnce({ validUntil: '2027-03-10' });
    show('/checks/pending');

    await userEvent.click(await screen.findByRole('button', { name: 'Pedir acceso' }));
    expect(await screen.findByText('Escribí el motivo del pedido de acceso.')).toBeVisible();
    expect(post).toHaveBeenLastCalledWith('/api/checks/k1/access-grants', { reason: null });

    await userEvent.type(screen.getByLabelText('Motivo'), 'Informar el hemograma');
    await userEvent.click(screen.getByRole('button', { name: 'Pedir acceso' }));
    expect(await screen.findByText('/clinical-records/p1 · Acceso autorizado hasta el 10/03/2027.')).toBeVisible();
    expect(post).toHaveBeenLastCalledWith('/api/checks/k1/access-grants', { reason: 'Informar el hemograma' });
  });
});

describe('my orders', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  const order = (overrides: Partial<MyOrder>): MyOrder => ({
    orderId: 'o1', patientId: 'p1', patientName: 'Ana Pérez', patientDocument: null, description: 'Hemograma',
    orderedAt: '2026-03-01T09:00:00', situation: 'OPEN', performedAt: null, performedByName: null, reportedAt: null,
    reportedByName: null, mayRead: true, ...overrides,
  });

  it('shows what waits and what was reported recently, all on asking, and the record only while it may be read', async () => {
    answering({ '/api/orders/mine': [
      order({ orderId: 'o1', description: 'Hemograma', mayRead: false }),
      order({ orderId: 'o2', description: 'Ecografía', situation: 'REPORTED', performedAt: '2000-01-01T09:00:00',
        reportedAt: '2000-01-02T09:00:00', reportedByName: 'Dr. Benítez' }),
    ] });
    show('/orders/mine');

    const waiting = (await screen.findByText('Hemograma')).closest('tr') as HTMLElement;
    expect(within(waiting).getByText('Abierta: nadie hizo el estudio todavía')).toBeVisible();
    expect(within(waiting).queryByText('Ver historia')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Con resultado en los últimos 30 días' })).toBeVisible();
    expect(screen.queryByText('Ecografía')).toBeNull();
    expect(screen.getByText('No hay resultados para mostrar.')).toBeVisible();

    await userEvent.click(screen.getByRole('button', { name: 'Ver todos los resultados' }));
    expect(screen.getByRole('heading', { name: 'Con resultado' })).toBeVisible();
    const done = screen.getByText('Ecografía').closest('tr') as HTMLElement;
    expect(within(done).getByRole('link', { name: 'Ver historia' })).toHaveAttribute('href', '/clinical-records/p1');
  });
});
