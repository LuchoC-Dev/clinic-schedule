import { QueryClientProvider, focusManager } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { check, clinicalRecord, consultation, order } from '../../test/record';
import type { Entry } from '../arrivals/arrivals';
import type { ClinicalRecord } from '../record/record';
import { RecordPage } from '../record/RecordPage';
import { api } from '../session';
import { MyQueuePage } from './MyQueuePage';
import type { MyQueue } from './queue';

vi.mock('../session', () => ({
  api: { get: vi.fn(), post: vi.fn(), upload: vi.fn() },
  BASE: '/',
  useUser: () => ({ username: 'arias', role: 'PRACTITIONER', practitionerId: 'd1', temporaryPassword: false }),
}));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const upload = vi.mocked(api.upload);

function entry(overrides: Partial<Entry> = {}): Entry {
  return {
    id: 'e1', patientId: 'p1', patient: 'Tomás Suárez', patientDocument: 'DNI 30123456', arrivedAt: '2026-03-10T10:12:00',
    emergency: false, appointmentId: 'a1', appointmentAt: '2026-03-10T10:00:00', reservedForId: null, reservedFor: null,
    state: 'CALLED', timesCalled: 1, withPractitionerId: 'd1', withPractitioner: 'Dra. Arias', ...overrides,
  };
}

function queue(called: Entry | null, waiting: Entry[] = []): MyQueue {
  return {
    queueId: 'q1', queueName: 'Consultorio 3', called,
    session: { queueId: 'q1', open: true, closesByItself: true, waiting, uncallable: [], called: called ? [called] : [], done: [] },
  };
}

function answering(mine: MyQueue | Error, record: ClinicalRecord = clinicalRecord({ mayRecord: true })) {
  get.mockImplementation(((path: string) => {
    if (path === '/api/my-queue') return mine instanceof Error ? Promise.reject(mine) : Promise.resolve(mine);
    if (path === '/api/clinical-records/p1') return Promise.resolve(record);
    return Promise.reject(new Error(path));
  }) as typeof api.get);
}

function show(path: string | { pathname: string; state: unknown } = '/my-queue') {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/my-queue" element={<MyQueuePage />} />
            <Route path="/clinical-records/:patientId" element={<RecordPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

const refusal = (status: number, code: string) => new ApiError(status, { status, error: 'x', message: 'x', code });

describe('my queue', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    upload.mockReset();
  });

  it('shows whom they are seeing with their record, and who waits, an emergency said so', async () => {
    answering(queue(entry(), [entry({ id: 'e2', patientId: 'p2', patient: 'Pedro Ibarra', patientDocument: null,
      appointmentId: null, appointmentAt: null, emergency: true, state: 'WAITING' })]));
    show();

    const seeing = await screen.findByText('Atendiendo');
    expect(seeing.parentElement).toHaveTextContent('Tomás Suárez — DNI 30123456');
    expect(seeing.parentElement).toHaveTextContent('Turno 10:00 · llegó 10:12');
    expect(screen.getByRole('button', { name: 'Terminar' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'No entró' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Llamar al siguiente' })).toBeNull();
    const waiting = screen.getByText('Pedro Ibarra').closest('li') as HTMLElement;
    expect(within(waiting).getByText('Urgencia')).toBeVisible();
    expect(within(waiting).getByText('Sin turno')).toBeVisible();
    expect(await screen.findByRole('heading', { name: 'Tomás Suárez' })).toBeVisible();
    expect(screen.getByText('Nueva consulta')).toBeVisible();
  });

  it('cannot call the next while nobody waits', async () => {
    answering(queue(null, []));
    show();
    expect(await screen.findByRole('button', { name: 'Llamar al siguiente' })).toBeDisabled();
    expect(screen.getByText('Nadie espera.')).toBeVisible();
  });

  it('calls the next, the one the queue says', async () => {
    answering(queue(null, [entry({ state: 'WAITING' })]));
    post.mockResolvedValue(queue(entry()));
    show();
    await userEvent.click(await screen.findByRole('button', { name: 'Llamar al siguiente' }));
    expect(post).toHaveBeenCalledWith('/api/my-queue/call-next');
  });

  /** Shown again as the queues change, from the server's stream (D-66). */
  it('is read again when the server says a queue changed', async () => {
    const streams: { listeners: Record<string, (event: MessageEvent<string>) => void> }[] = [];
    vi.stubGlobal('EventSource', class {
      listeners: Record<string, (event: MessageEvent<string>) => void> = {};
      constructor() { streams.push(this); }
      addEventListener(type: string, listener: (event: MessageEvent<string>) => void) { this.listeners[type] = listener; }
      close() {}
    });
    try {
      answering(queue(null, []));
      show();
      await screen.findByText('Nadie espera.');
      answering(queue(null, [entry({ state: 'WAITING', patient: 'Camila Molina' })]));
      streams[0].listeners.queue(new MessageEvent('queue', { data: 'q1' }));
      expect(await screen.findByText(/Camila Molina/)).toBeVisible();
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("says so when they serve no queue, and a refusal in the queue's words", async () => {
    answering(refusal(409, 'NO_QUEUE_SERVED'));
    show();
    expect(await screen.findByText('No atendés ninguna cola. Recepción las arma.')).toBeVisible();
  });

  it('asks before finishing with a consultation typed and not sent, and finishes at once otherwise', async () => {
    answering(queue(entry()));
    post.mockResolvedValue(queue(null));
    show();
    await userEvent.click(await screen.findByRole('button', { name: 'Terminar' }));
    expect(post).toHaveBeenCalledWith('/api/my-queue/finish');

    post.mockReset();
    post.mockResolvedValue(queue(null));
    await userEvent.type(await screen.findByLabelText('Motivo y evolución'), 'Dolor de cabeza');
    await userEvent.click(screen.getByRole('button', { name: 'No entró' }));
    const asked = await screen.findByRole('dialog');
    expect(asked).toHaveTextContent('Escribiste una consulta que todavía no guardaste. Si seguís, se pierde.');
    expect(post).not.toHaveBeenCalled();
    await userEvent.click(within(asked).getByRole('button', { name: 'Volver a la consulta' }));
    expect(screen.getByLabelText('Motivo y evolución')).toHaveValue('Dolor de cabeza');
    await userEvent.click(screen.getByRole('button', { name: 'No entró' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'No entró: descartarla' }));
    expect(post).toHaveBeenCalledWith('/api/my-queue/did-not-come-in');
  });
});

describe('the record of a patient', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    upload.mockReset();
  });

  it('offers no consultation unless the patient arrived to be seen today, and says a record they may not read', async () => {
    answering(queue(null), clinicalRecord({ mayRecord: false }));
    show('/clinical-records/p1');
    expect(await screen.findByRole('heading', { name: 'Tomás Suárez' })).toBeVisible();
    expect(screen.getByText(/^DNI 30123456 · \d+ años · OSDE · plan 310 · identidad verificada$/)).toBeVisible();
    expect(screen.queryByText('Nueva consulta')).toBeNull();
    expect(screen.getByText('Todavía no hay nada registrado.')).toBeVisible();

    get.mockReset();
    get.mockRejectedValue(refusal(403, 'NO_ACCESS'));
    show('/clinical-records/p1');
    expect(await screen.findByText('No podés leer esta historia clínica.')).toBeVisible();
  });

  it('sends the consultation with its rows, and keeps what was typed when it is refused', async () => {
    answering(queue(null), clinicalRecord({ mayRecord: true,
      consultations: [consultation({ orders: [order({ id: 'o1', description: 'Laboratorio' })] })] }));
    post.mockRejectedValueOnce(refusal(403, 'NOT_ARRIVED'));
    show('/clinical-records/p1');

    await userEvent.type(await screen.findByLabelText('Motivo y evolución'), 'Control');
    await userEvent.click(screen.getByRole('button', { name: '+ Receta' }));
    await userEvent.type(screen.getByLabelText('Receta 1'), 'enalapril 10 mg');
    await userEvent.click(screen.getByRole('button', { name: '+ Orden' }));
    await userEvent.type(screen.getByLabelText('Orden 1: qué se pide'), 'Ecografía');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar consulta' }));

    expect(await screen.findByText('La consulta se registra cuando el paciente llegó a atenderse con vos hoy.')).toBeVisible();
    expect(screen.getByLabelText('Motivo y evolución')).toHaveValue('Control');
    const [path, sent] = post.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe('/api/consultations');
    expect(sent).toMatchObject({ patientId: 'p1', notes: 'Control', items: [{ kind: 'PRESCRIPTION', description: 'enalapril 10 mg' }],
      checks: [], orders: [{ description: 'Ecografía' }] });

    post.mockResolvedValueOnce({});
    await userEvent.click(screen.getByRole('button', { name: 'Guardar consulta' }));
    expect(await screen.findByText('Consulta registrada.')).toBeVisible();
    expect((post.mock.calls[1] as [string, { id: string }])[1].id).toBe((sent as { id: string }).id);
    expect(screen.getByLabelText('Motivo y evolución')).toHaveValue('');
  });

  it('lets only its author cancel an order, saying why', async () => {
    answering(queue(null), clinicalRecord({ consultations: [consultation({ orders: [
      order({ id: 'o1', description: 'Mía' }), order({ id: 'o2', description: 'De otro', orderedBy: 'd2' })] })] }));
    post.mockResolvedValue({});
    show('/clinical-records/p1');
    await userEvent.click(await screen.findByRole('tab', { name: 'Órdenes' }));
    const panel = screen.getByRole('tabpanel');
    expect(within(panel).getAllByRole('button', { name: 'Anular' })).toHaveLength(1);
    await userEvent.click(within(panel).getByRole('button', { name: 'Anular' }));
    await userEvent.click(within(panel).getByRole('button', { name: 'Anular orden' }));
    expect(within(panel).getByText('Escribí el motivo para anular la orden.')).toBeVisible();
    await userEvent.type(within(panel).getByLabelText('Motivo para anularla'), 'Se hizo en otro lado');
    await userEvent.click(within(panel).getByRole('button', { name: 'Anular orden' }));
    expect(post).toHaveBeenCalledWith('/api/orders/o1/cancel', { reason: 'Se hizo en otro lado' });
  });

  it("records a pending check's result with its file attached first, and refuses one with neither", async () => {
    answering(queue(null), clinicalRecord({ checks: [check({ id: 'k1' })] }));
    upload.mockResolvedValue({});
    post.mockResolvedValue({});
    show('/clinical-records/p1');
    await userEvent.click(await screen.findByRole('tab', { name: 'Estudios' }));
    const panel = screen.getByRole('tabpanel');
    await userEvent.click(within(panel).getByRole('button', { name: 'Cargar resultado' }));
    expect(within(panel).getByText('Escribí el resultado o adjuntá el archivo que lo trae.')).toBeVisible();

    const pdf = new File(['%PDF-1.4'], 'hemograma.pdf', { type: 'application/pdf' });
    await userEvent.upload(panel.querySelector('input[type="file"]') as HTMLInputElement, pdf);
    await userEvent.click(within(panel).getByRole('button', { name: 'Cargar resultado' }));
    expect(await within(panel).findByText('Resultado cargado.')).toBeVisible();
    expect(upload).toHaveBeenCalledWith('/api/checks/k1/attachments', pdf);
    expect(post).toHaveBeenCalledWith('/api/checks/k1/result', { text: null });
    expect(upload.mock.invocationCallOrder[0]).toBeLessThan(post.mock.invocationCallOrder[0]);
  });

  it('says until when access was granted, when that is how it was reached', async () => {
    answering(queue(null), clinicalRecord());
    show({ pathname: '/clinical-records/p1', state: { notice: 'Acceso autorizado hasta el 10/03/2027.' } });
    expect(await screen.findByRole('status')).toHaveTextContent('Acceso autorizado hasta el 10/03/2027.');
  });

  it('reads the record once, and not again when the window regains focus', async () => {
    answering(queue(null), clinicalRecord());
    show('/clinical-records/p1');
    await screen.findByRole('heading', { name: 'Tomás Suárez' });
    focusManager.setFocused(false);
    focusManager.setFocused(true);
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(get.mock.calls.filter(([path]) => path === '/api/clinical-records/p1')).toHaveLength(1);
    focusManager.setFocused(undefined);
  });
});
