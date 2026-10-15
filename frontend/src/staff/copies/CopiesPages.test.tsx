import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { Layout } from '../Layout';
import { api } from '../session';
import type { RecordCopy } from './copies';
import { recordCopy } from '../../test/copies';
import { RecordCopiesPage } from './RecordCopiesPage';
import { RecordCopyPage } from './RecordCopyPage';

vi.mock('../session', () => ({
  api: { get: vi.fn(), post: vi.fn(), upload: vi.fn() },
  BASE: '/',
  logOut: vi.fn(),
  useUser: () => ({ username: 'arias', role: 'PRACTITIONER', practitionerId: 'd1', temporaryPassword: false }),
}));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

function Where() {
  return <p>en {useLocation().pathname}</p>;
}

function show(path: string) {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={[path]}>
          <Layout>
            <Routes>
              <Route path="/record-copies" element={<RecordCopiesPage />} />
              <Route path="/record-copies/:id" element={<RecordCopyPage />} />
              <Route path="*" element={<Where />} />
            </Routes>
          </Layout>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

function answering(list: RecordCopy[], one?: () => RecordCopy, settings = { reasonRequired: true }) {
  get.mockImplementation(((path: string, query?: Record<string, string>) => {
    if (path === '/api/record-copies' && query?.code === 'K7QM-3XPA-9TRD') return Promise.resolve(recordCopy());
    if (path === '/api/record-copies' && query?.code) {
      return Promise.reject(new ApiError(404, { status: 404, error: 'x', message: 'x', code: 'RECORD_COPY_NOT_FOUND' }));
    }
    if (path === '/api/record-copies') return Promise.resolve(list);
    if (path === '/api/record-copies/r1' && one) return Promise.resolve(one());
    if (path === '/api/access-grants/settings') return Promise.resolve(settings);
    return Promise.reject(new Error(path));
  }) as typeof api.get);
}

describe('the copies asked for', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it('lists those waiting with the time left, counted in the menu, and finds one by its code', async () => {
    answering([recordCopy(), recordCopy({ id: 'r2', patient: { id: 'p2', fullName: 'Juan Gómez', document: null },
      requestedBy: { id: 'm1', fullName: 'Rosa Gómez', document: null }, minutesLeft: -190, late: true, mayRead: false })],
    () => recordCopy());
    show('/record-copies');

    const late = (await screen.findByText('Juan Gómez')).closest('tr') as HTMLElement;
    expect(within(late).getByText('Pidió: Rosa Gómez')).toBeVisible();
    expect(within(late).getByText('Vencida hace 3 h')).toBeVisible();
    expect(within(late).getByRole('button', { name: 'Ver pedido' })).toBeVisible();
    const due = screen.getByText('Ana Pérez — DNI 30111222').closest('tr') as HTMLElement;
    expect(within(due).getByText('Quedan 31 h')).toBeVisible();
    expect(within(due).getByRole('button', { name: 'Revisar' })).toBeVisible();
    expect(screen.getByLabelText('2 copias pedidas esperando')).toHaveTextContent('2');

    await userEvent.type(screen.getByLabelText('Código de verificación'), 'ZZZZ');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByText('No hay ninguna copia con ese código.')).toBeVisible();
    await userEvent.clear(screen.getByLabelText('Código de verificación'));
    await userEvent.type(screen.getByLabelText('Código de verificación'), 'K7QM-3XPA-9TRD');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByRole('heading', { name: 'Copia de la historia de Ana Pérez — DNI 30111222' })).toBeVisible();
  });

  it('counts nothing in the menu while none waits', async () => {
    answering([]);
    show('/record-copies');
    expect(await screen.findByText('No hay copias esperando.')).toBeVisible();
    expect(screen.queryByLabelText(/copias pedidas esperando/)).toBeNull();
  });
});

describe('a copy', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it('is opened in the browser, put together again, and authenticated saying it was reviewed, of the assembly seen', async () => {
    answering([recordCopy()], () => recordCopy({ assembly: 2 }));
    post.mockResolvedValue(recordCopy());
    show('/record-copies/r1');

    expect(await screen.findByText('Quedan 31 h')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Ver la copia' })).toHaveAttribute('href', '/api/record-copies/r1/pdf');
    expect(screen.getByRole('link', { name: 'Ver la copia' })).toHaveAttribute('target', '_blank');
    await userEvent.click(screen.getByRole('button', { name: 'Volver a armar la copia' }));
    expect(await screen.findByText('Copia armada de nuevo con la historia de ahora. Revisala antes de autenticarla.'))
      .toBeVisible();
    expect(post).toHaveBeenCalledWith('/api/record-copies/r1/reassembly');

    post.mockRejectedValueOnce(new ApiError(409, { status: 409, error: 'x', message: 'x', code: 'NOT_REVIEWED' }));
    await userEvent.click(screen.getByRole('button', { name: 'Autenticar copia' }));
    expect(post).toHaveBeenLastCalledWith('/api/record-copies/r1/authentication', { assembly: 2, reviewed: false });
    expect(await screen.findByText('Marcá que revisaste la copia antes de autenticarla.')).toBeVisible();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Revisé la copia y está completa' }));
    await userEvent.click(screen.getByRole('button', { name: 'Autenticar copia' }));
    expect(post).toHaveBeenLastCalledWith('/api/record-copies/r1/authentication', { assembly: 2, reviewed: true });
    expect(await screen.findByText('Copia autenticada. El paciente ya puede descargarla desde su cuenta.')).toBeVisible();
  });

  it('once authenticated on paper, is printed and taken to reception, and nothing more is offered', async () => {
    answering([], () => recordCopy({ delivery: 'PAPER', authentication: { practitionerId: 'd1',
      practitionerName: 'Dra. Arias', username: 'arias', at: '2026-03-10T11:00:00' } }));
    show('/record-copies/r1');
    expect(await screen.findByRole('link', { name: 'Ver e imprimir la copia' })).toBeVisible();
    expect(screen.getByText('Dra. Arias, 10/03/2026 11:00')).toBeVisible();
    expect(screen.queryByText('Quedan 31 h')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Autenticar copia' })).toBeNull();
  });

  it('asks for access to a record that may not be read, the reason optional or not, and then may be reviewed', async () => {
    let mayRead = false;
    answering([], () => recordCopy({ mayRead }), { reasonRequired: false });
    post.mockImplementation((async () => { mayRead = true; return { validUntil: '2027-03-10' }; }) as typeof api.post);
    show('/record-copies/r1');

    expect(await screen.findByText('No tenés acceso a esta historia. Para revisar y autenticar la copia, pedilo.')).toBeVisible();
    expect(screen.queryByRole('link', { name: 'Ver la copia' })).toBeNull();
    await userEvent.type(await screen.findByLabelText('Motivo (opcional)'), 'Autenticar la copia');
    await userEvent.click(screen.getByRole('button', { name: 'Pedir acceso' }));
    expect(post).toHaveBeenCalledWith('/api/record-copies/r1/access-grants', { reason: 'Autenticar la copia' });
    expect(await screen.findByText('Acceso autorizado hasta el 10/03/2027.')).toBeVisible();
    expect(await screen.findByRole('link', { name: 'Ver la copia' })).toBeVisible();
  });
});
