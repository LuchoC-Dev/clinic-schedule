import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { EnvelopesPage } from '../copies/EnvelopesPage';
import { api } from '../session';
import { PractitionerPage } from './PractitionerPage';
import type { PractitionerSetup } from './practitioners';
import { QueuePage } from './QueuePages';

vi.mock('../session', () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const put = vi.mocked(api.put);
const remove = vi.mocked(api.delete);

const ARIAS: PractitionerSetup = {
  id: 'p1', fullName: 'Dra. Laura Arias',
  reasons: [{ id: 'r1', name: 'consulta', durationMinutes: 30, webBookable: false }],
  weeklySchedule: [{ day: 'TUESDAY', startsAt: '09:00:00', endsAt: '13:00:00' }],
  timeOff: [
    { startsAt: '2026-03-02T00:00:00', endsAt: '2026-03-03T00:00:00' },
    // Over by the browser's clock in UTC, not yet at the practice.
    { startsAt: '2026-03-10T08:00:00', endsAt: '2026-03-10T11:00:00' },
    { startsAt: '2026-03-12T00:00:00', endsAt: '2026-03-14T00:00:00' },
  ],
};

function show(address: string) {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={[address]}>
          <Routes>
            <Route path="/practitioners/:id" element={<PractitionerPage />} />
            <Route path="/queues/:id" element={<QueuePage />} />
            <Route path="/record-copy-envelopes" element={<EnvelopesPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

function answer(paths: Record<string, unknown>) {
  get.mockImplementation(((path: string) => (path in paths ? Promise.resolve(paths[path]) : Promise.reject(new Error(path)))) as typeof api.get);
}

beforeEach(() => {
  for (const mock of [get, post, put, remove]) mock.mockReset();
  // Tuesday 10 March 2026, 9:30 at the practice.
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-03-10T12:30:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe("a practitioner's set-up", () => {
  beforeEach(() => answer({ '/api/practitioners/p1': ARIAS }));

  it('shows the time off not over yet, and the week with three empty rows', async () => {
    show('/practitioners/p1');
    await screen.findByRole('heading', { name: 'Dra. Laura Arias' });

    expect(within(screen.getByRole('list', { name: 'Ausencias próximas' })).getAllByRole('listitem').map((item) => item.textContent))
      .toEqual(['mar 10/03/2026 de 08:00 a 11:00', 'del jue 12/03/2026 al vie 13/03/2026']);
    expect(screen.getAllByRole('group', { name: /^Tramo/ })).toHaveLength(4);
    expect(within(screen.getByRole('group', { name: 'Tramo 1' })).getByLabelText('Día')).toHaveValue('TUESDAY');
    expect(within(screen.getByRole('group', { name: 'Tramo 1' })).getByLabelText('Desde')).toHaveValue('09:00');
    expect(screen.getByLabelText(/^Desde el día/)).toHaveValue('2026-03-10');
    expect(screen.getByRole('link', { name: 'Ver su agenda' })).toHaveAttribute('href', '/agenda?practitionerId=p1');
  });

  it('refuses a row half filled before sending the week, and sends it whole once complete', async () => {
    put.mockResolvedValue({ ...ARIAS, weeklySchedule: [ARIAS.weeklySchedule[0], { day: 'FRIDAY', startsAt: '14:00:00', endsAt: '18:00:00' }] });
    show('/practitioners/p1');
    const second = await screen.findByRole('group', { name: 'Tramo 2' });

    await userEvent.selectOptions(within(second).getByLabelText('Día'), 'FRIDAY');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar semana' }));
    expect(put).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('Completá día, desde y hasta en cada fila, o dejala vacía para sacarla.');

    await userEvent.type(within(second).getByLabelText('Desde'), '14:00');
    await userEvent.type(within(second).getByLabelText('Hasta'), '18:00');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar semana' }));
    expect(put).toHaveBeenCalledWith('/api/practitioners/p1/weekly-schedule', { hours: [
      { day: 'TUESDAY', startsAt: '09:00', endsAt: '13:00' }, { day: 'FRIDAY', startsAt: '14:00', endsAt: '18:00' }] });
    expect(await screen.findByRole('status')).toHaveTextContent('Semana guardada.');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getAllByRole('group', { name: /^Tramo/ })).toHaveLength(5);
  });

  it('saves a reason as booked on the web, and says a duplicate', async () => {
    put.mockResolvedValueOnce({ id: 'r1', name: 'consulta', durationMinutes: 40, webBookable: true });
    show('/practitioners/p1');
    const reason = await screen.findByRole('form', { name: 'Razón consulta' });

    await userEvent.clear(within(reason).getByLabelText(/^Minutos/));
    await userEvent.type(within(reason).getByLabelText(/^Minutos/), '40');
    await userEvent.click(within(reason).getByLabelText('Se reserva por la web'));
    await userEvent.click(within(reason).getByRole('button', { name: 'Guardar' }));
    expect(put).toHaveBeenCalledWith('/api/practitioners/p1/reasons/r1', { name: 'consulta', durationMinutes: 40, webBookable: true });
    expect(await within(reason).findByRole('status')).toHaveTextContent('Razón guardada.');

    post.mockRejectedValueOnce(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'DUPLICATE_REASON' }));
    await userEvent.type(screen.getByLabelText(/^Nueva razón/), 'consulta');
    await userEvent.click(screen.getByRole('button', { name: 'Agregar' }));
    expect(post).toHaveBeenCalledWith('/api/practitioners/p1/reasons', { name: 'consulta', durationMinutes: 30 });
    expect(await screen.findByRole('alert')).toHaveTextContent('Ya tiene una razón con ese nombre.');
    expect(screen.getByLabelText(/^Nueva razón/)).toHaveValue('consulta');
  });

  it('records part of a day off, saying what it cancelled and who arrived already', async () => {
    post.mockResolvedValue({
      cancelled: [{ id: 'a1', patientId: 'x1', startsAt: '2026-03-10T11:30:00' }],
      needsManualHandling: [{ id: 'a2', patientId: 'x2', startsAt: '2026-03-10T11:00:00' }],
      patients: { x1: 'Ana Pérez', x2: 'Juan Gómez' },
    });
    show('/practitioners/p1');
    await screen.findByRole('heading', { name: 'Dra. Laura Arias' });

    await userEvent.type(screen.getByLabelText(/^Desde la hora/), '11:00');
    await userEvent.type(screen.getByLabelText(/^Hasta la hora/), '13:00');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar ausencia' }));

    expect(post).toHaveBeenCalledWith('/api/time-off',
      { practitionerId: 'p1', date: '2026-03-10', untilDate: null, startsAt: '11:00', endsAt: '13:00' });
    expect(await screen.findByRole('status')).toHaveTextContent('Ausencia registrada. Turnos cancelados (1): mar 10/03 11:30 Ana Pérez.');
    expect(screen.getByRole('alert')).toHaveTextContent('resolvelos a mano. mar 10/03 11:00 Juan Gómez.');
  });
});

describe('a queue', () => {
  beforeEach(() => answer({
    '/api/queues/q1': { id: 'q1', name: 'Guardia', room: '3', practitionerIds: ['p1'] },
    '/api/queues': [{ id: 'q1', name: 'Guardia', room: '3', practitionerIds: ['p1'] },
      { id: 'q2', name: 'Clínica', room: null, practitionerIds: ['p3'] }],
    '/api/practitioners': [{ id: 'p1', fullName: 'Dra. Arias', reasons: [] }, { id: 'p2', fullName: 'Dr. Benítez', reasons: [] },
      { id: 'p3', fullName: 'Lic. Paz', reasons: [] }],
  }));

  it('offers only who serves no queue, and adds and takes out its practitioners', async () => {
    post.mockResolvedValue({});
    remove.mockResolvedValue({});
    show('/queues/q1');
    await screen.findByText('Consultorio 3');

    const offered = await screen.findByLabelText('Agregar profesional');
    expect(within(offered).getAllByRole('option').map((option) => option.textContent)).toEqual(['Dr. Benítez']);
    await userEvent.click(screen.getByRole('button', { name: 'Agregar' }));
    expect(post).toHaveBeenCalledWith('/api/queues/q1/practitioners', { practitionerId: 'p2' });
    expect(await screen.findByRole('status')).toHaveTextContent('Profesional agregado a la cola.');

    await userEvent.click(screen.getByRole('button', { name: 'Quitar a Dra. Arias' }));
    expect(remove).toHaveBeenCalledWith('/api/queues/q1/practitioners/p1');
    expect(await screen.findByText('Profesional quitado de la cola.')).toBeInTheDocument();
  });

  it('renames it with no room when none is typed, and says a name taken', async () => {
    put.mockRejectedValue(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'QUEUE_NAME_TAKEN' }));
    show('/queues/q1');
    await screen.findByText('Consultorio 3');

    await userEvent.clear(screen.getByLabelText(/^Consultorio/));
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(put).toHaveBeenCalledWith('/api/queues/q1', { name: 'Guardia', room: null });
    expect(await screen.findByRole('alert')).toHaveTextContent('Ya hay otra cola con ese nombre.');
  });
});

describe('the copies to collect', () => {
  beforeEach(() => answer({
    '/api/record-copy-envelopes': [
      { id: 'c1', patient: { id: 'a', fullName: 'Ana Pérez', document: 'DNI 30111222' },
        requestedBy: { id: 'a', fullName: 'Ana Pérez', document: 'DNI 30111222' }, authenticatedAt: '2026-03-09T11:00:00' },
      { id: 'c2', patient: { id: 'b', fullName: 'Sofía Gómez', document: null },
        requestedBy: { id: 'm', fullName: 'Marta Gómez', document: 'DNI 20111222' }, authenticatedAt: '2026-03-10T08:05:00' },
    ],
  }));

  it('says whose each is and who asked, and hands one over only with the document seen', async () => {
    show('/record-copy-envelopes');
    const own = (await screen.findByText('Ana Pérez — DNI 30111222')).closest('tr') as HTMLElement;
    expect(within(own).getByText('El paciente')).toBeInTheDocument();
    expect(within(own).getByText('09/03/2026 11:00')).toBeInTheDocument();
    const child = screen.getByText('Sofía Gómez').closest('tr') as HTMLElement;
    expect(within(child).getByText('Marta Gómez — DNI 20111222')).toBeInTheDocument();

    post.mockRejectedValueOnce(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'DOCUMENT_NOT_SEEN' }));
    // The document seen of whoever collects another envelope says nothing of this one.
    await userEvent.click(within(own).getByLabelText('Vi el documento de quien la retira'));
    await userEvent.click(within(child).getByRole('button', { name: /^Entregar/ }));
    expect(post).toHaveBeenLastCalledWith('/api/record-copy-envelopes/c2/handover', { documentSeen: false });
    expect(await screen.findByRole('alert')).toHaveTextContent('Marcá que viste el documento de quien la retira.');

    post.mockResolvedValueOnce({});
    await userEvent.click(within(child).getByLabelText('Vi el documento de quien la retira'));
    await userEvent.click(within(child).getByRole('button', { name: /^Entregar/ }));
    expect(post).toHaveBeenLastCalledWith('/api/record-copy-envelopes/c2/handover', { documentSeen: true });
    expect(await screen.findByRole('status')).toHaveTextContent('Copia entregada.');
  });
});
