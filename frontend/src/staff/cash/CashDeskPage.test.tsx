import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api';
import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { CancellationsPage } from '../logs/CancellationsPage';
import { RemindersPage } from '../logs/RemindersPage';
import { api } from '../session';
import type { CashDay, Movement } from './cash';
import { CashDeskPage } from './CashDeskPage';

vi.mock('../session', () => ({ api: { get: vi.fn(), post: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

const ANA = { id: 'p1', fullName: 'Ana Pérez', document: null };
const JUAN = { id: 'p2', fullName: 'Juan Gómez', document: null };
const ROSA = { id: 'p3', fullName: 'Rosa Díaz', document: null };
const sum = (count: number, amount: number) => ({ count, amount });
const TOTAL = { charged: sum(2, 25000), refunded: sum(1, 10000), left: 15000, drawer: 15000,
  byMeans: [{ means: 'CASH', charged: sum(2, 25000), refunded: sum(1, 10000), left: 15000 }] };

function movement(overrides: Partial<Movement>): Movement {
  return {
    kind: 'CHARGE', visitId: 'v1', patient: ANA, emergency: false, copay: false, amount: 15000, means: 'CASH', left: 15000,
    reason: null, recordedBy: 'recepcion', recordedAt: '2026-03-10T10:15:00', refundable: true, chargeableAgain: false,
    ...overrides,
  };
}

const DAY: CashDay = {
  date: '2026-03-10',
  movements: [
    movement({}),
    movement({ visitId: 'v2', patient: JUAN, amount: 10000, left: 0, refundable: false, chargeableAgain: true }),
    movement({ kind: 'REFUND', visitId: 'v2', patient: JUAN, amount: 10000, left: null, reason: 'se fue', refundable: false }),
    // A charge no longer its visit's current one: what is left of it is not given back from here.
    movement({ visitId: 'v6', patient: { id: 'p6', fullName: 'Leo Viejo', document: null }, amount: 5000, left: 5000,
      refundable: false }),
  ],
  total: TOTAL,
  byRecorder: [{ ...TOTAL, recordedBy: 'recepcion' }],
  pending: [{ visitId: 'v3', patient: ROSA, arrivedAt: '2026-03-10T09:40:00' }],
  covered: [
    { visitId: 'v4', patient: { id: 'p4', fullName: 'Luis Paz', document: null }, emergency: false, insurer: 'OSDE',
      memberNumber: null, plan: '310', token: '123456', recordedBy: 'recepcion', recordedAt: '2026-03-10T09:00:00',
      withdrawal: null, chargeable: false },
    { visitId: 'v5', patient: { id: 'p5', fullName: 'Ema Sol', document: null }, emergency: true, insurer: 'IOMA',
      memberNumber: null, plan: null, token: null, recordedBy: 'recepcion', recordedAt: '2026-03-10T09:30:00',
      withdrawal: { reason: 'no tenía la credencial', recordedBy: 'otra', recordedAt: '' }, chargeable: true },
  ],
};

function show(address: string, element = <CashDeskPage />) {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <MemoryRouter initialEntries={[address]}>
          <Routes><Route path="*" element={element} /></Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
  );
}

const rowOf = (text: string, index = 0) => screen.getAllByText(text)[index].closest('tr') as HTMLElement;

describe("the day's cash desk", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockResolvedValue(DAY);
    post.mockResolvedValue(DAY);
  });

  it('shows the day: its tiles, totals and movements, a refund with a minus and why', async () => {
    show('/cash-desk?date=2026-03-10');

    expect(await screen.findByText('Efectivo en caja')).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/cash-desk', { date: '2026-03-10' });
    expect(screen.getAllByText('$ 15.000,00').length).toBeGreaterThan(0);
    const refund = rowOf('Devolución');
    expect(within(refund).getByText('−$ 10.000,00')).toBeInTheDocument();
    expect(within(refund).getByText(/se fue/)).toBeInTheDocument();
    expect(within(refund).queryByRole('button')).not.toBeInTheDocument();
    expect(within(rowOf('Leo Viejo')).queryByRole('button')).not.toBeInTheDocument();
    expect(within(rowOf('Ana Pérez')).getByRole('button', { name: 'Devolver (queda $ 15.000,00)' })).toBeInTheDocument();
    expect(within(rowOf('Ana Pérez')).queryByRole('button', { name: 'Cobrar de nuevo' })).not.toBeInTheDocument();
    expect(screen.getByText(/OSDE · plan 310/)).toBeInTheDocument();
    expect(screen.getByText(/cobertura quitada: no tenía la credencial \(otra\)/)).toBeInTheDocument();
  });

  /** What is left of a visit's current charge may be given back, saying why; a large amount asks first (D-88, D-101). */
  it('gives back what is left of a charge, asking once more for a very large amount', async () => {
    get.mockResolvedValue({ ...DAY, movements: [movement({ amount: 900000, left: 900000 })] });
    show('/cash-desk');

    await userEvent.click(await screen.findByRole('button', { name: 'Devolver (queda $ 900.000,00)' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText(/Importe a devolver/)).toHaveValue('900000');
    await userEvent.type(within(dialog).getByLabelText(/Motivo/), 'cobrado de más');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Devolver' }));
    expect(post).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sí, devolver $ 900.000,00' }));

    expect(post).toHaveBeenCalledWith('/api/visits/v1/refund', { amount: 900000, reason: 'cobrado de más' });
    expect(await screen.findByRole('status')).toHaveTextContent('Devolución registrada.');
  });

  /** A visit given back whole is charged again (D-88). */
  it('charges again a visit given back whole', async () => {
    show('/cash-desk');
    const juan = await screen.findAllByText('Juan Gómez').then(() => rowOf('Juan Gómez'));

    expect(within(juan).queryByRole('button', { name: /Devolver/ })).not.toBeInTheDocument();
    await userEvent.click(within(juan).getByRole('button', { name: 'Cobrar de nuevo' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).queryByLabelText('Con cobertura')).not.toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText(/Importe/), '8000');
    await userEvent.selectOptions(within(dialog).getByLabelText('Medio de pago'), 'TRANSFER');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cobrar' }));

    expect(post).toHaveBeenCalledWith('/api/visits/v2/charge', { amount: 8000, means: 'TRANSFER' });
    expect(await screen.findByRole('status')).toHaveTextContent('Cobro registrado.');
  });

  /** An emergency left pending is charged, or recorded as covered with its copay (D-87, D-90). */
  it("charges an emergency left pending, or records it covered with its token and copay", async () => {
    show('/cash-desk');
    const rosa = await screen.findByText('Rosa Díaz').then((name) => name.closest('tr') as HTMLElement);

    await userEvent.click(within(rosa).getByRole('button', { name: 'Con cobertura' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Token (si lo pide)'), 'T-9');
    await userEvent.type(within(dialog).getByLabelText(/Coseguro/), '2.000');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Registrar con cobertura' }));

    expect(post).toHaveBeenCalledWith('/api/visits/v3/coverage', { token: 'T-9', copay: 2000, means: 'CASH' });
    expect(await screen.findByRole('status')).toHaveTextContent('Cobertura registrada.');
  });

  /** A coverage marked by mistake is withdrawn, saying why, and the visit charged privately (D-90). */
  it('withdraws a coverage standing, and charges privately one withdrawn', async () => {
    post.mockRejectedValueOnce(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'COPAY_LEFT' }));
    show('/cash-desk');
    const luis = await screen.findByText('Luis Paz').then((name) => name.closest('tr') as HTMLElement);
    const ema = rowOf('Ema Sol');

    expect(within(luis).queryByRole('button', { name: 'Cobrar' })).not.toBeInTheDocument();
    expect(within(ema).queryByRole('button', { name: 'Quitar cobertura' })).not.toBeInTheDocument();
    await userEvent.click(within(luis).getByRole('button', { name: 'Quitar cobertura' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/Motivo/), 'no era de la obra social');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Quitar cobertura' }));
    expect(post).toHaveBeenCalledWith('/api/visits/v4/coverage/withdrawal', { reason: 'no era de la obra social' });
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Primero devolvé el coseguro');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    await userEvent.click(within(ema).getByRole('button', { name: 'Cobrar' }));
    const charge = await screen.findByRole('dialog');
    await userEvent.type(within(charge).getByLabelText(/Importe/), '15000');
    await userEvent.click(within(charge).getByRole('button', { name: 'Cobrar' }));
    expect(post).toHaveBeenLastCalledWith('/api/visits/v5/charge', { amount: 15000, means: 'CASH' });
  });
});

describe('the cancellations and the reminders', () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('lists the cancellations with who, how and who asked, and says when there are more', async () => {
    get.mockImplementation(((path: string) => path === '/api/practitioners' ? Promise.resolve([]) : Promise.resolve({
      from: '2026-03-01', to: '2026-03-10', limitReached: true,
      cancellations: [{ appointmentId: 'a1', startsAt: '2026-03-12T10:00:00', practitioner: { id: 'd1', fullName: 'Dra. Arias' },
        patient: { id: 'p1', fullName: 'Ana Pérez', document: null }, cancelledAt: '2026-03-09T11:00:00',
        cancelledBy: 'portal:x', origin: 'ONLINE', requestedBy: 'PATIENT', note: null }],
    })) as typeof api.get);
    show('/cancellations?from=2026-03-01', <CancellationsPage />);

    expect(await screen.findByText('el paciente (web)')).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/cancellations', { practitionerId: null, from: '2026-03-01', to: null });
    expect(screen.getByText('Por la web')).toBeInTheDocument();
    expect(screen.getByText('el paciente')).toBeInTheDocument();
    expect(screen.getByText('jue 12/03/2026 10:00')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Hay más cancelaciones de las que se muestran');
    expect(screen.getByRole('link', { name: 'Ana Pérez' })).toHaveAttribute('href', '/patients/p1/appointments');
  });

  it('lists the reminders, the failed ones to call about', async () => {
    get.mockResolvedValue({ from: '2026-03-10', to: '2026-03-12', reminders: [
      { appointmentId: 'a1', startsAt: '2026-03-12T10:00:00', practitioner: { id: 'd1', fullName: 'Dra. Arias' },
        patient: { id: 'p1', fullName: 'Ana Pérez', document: 'DNI 30111222' }, outcome: 'FAILED', reason: null,
        recordedAt: '2026-03-10T10:00:00' }] });
    show('/reminders', <RemindersPage />);

    expect(await screen.findByText('No se pudo enviar: avisale por teléfono')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ana Pérez — DNI 30111222' })).toHaveAttribute('href', '/patients/p1');
  });

  it('says a day range backwards in Spanish', async () => {
    get.mockRejectedValue(new ApiError(400, { status: 400, error: 'Bad request', message: 'x', code: 'ILLEGAL_ARGUMENT' }));
    show('/reminders?from=2026-03-12&to=2026-03-10', <RemindersPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('El primer día es posterior al último.');
  });
});
