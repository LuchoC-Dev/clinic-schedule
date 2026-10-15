import { QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { newQueryClient } from '../../shared/queries';
import { Theme } from '../../shared/theme';
import { api } from '../session';
import type { RoomQueue } from './room';
import { WaitingRoomPage } from './WaitingRoomPage';

vi.mock('../session', () => ({ api: { get: vi.fn() }, BASE: '/' }));

const get = vi.mocked(api.get);

/** The server's stream of the queues, driven by the test. */
class FakeStream {
  static last: FakeStream;
  static readonly CLOSED = 2;
  readonly url: string;
  readyState = 1;
  closed = false;
  private readonly listeners: Record<string, ((event: MessageEvent<string>) => void)[]> = {};

  constructor(url: string) {
    this.url = url;
    FakeStream.last = this;
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    (this.listeners[type] ??= []).push(listener);
  }

  close() {
    this.closed = true;
  }

  emit(type: string, data = '') {
    for (const listener of this.listeners[type] ?? []) listener(new MessageEvent(type, { data }));
  }
}

const ROMERO: RoomQueue = { id: 'q1', name: 'Dra. Romero', room: '2', open: true, called: [], waiting: ['Tomás Suárez', 'Camila Méndez'] };
const reload = vi.fn();

function show() {
  return render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <WaitingRoomPage reload={reload} />
      </QueryClientProvider>
    </Theme>,
  );
}

beforeEach(() => {
  get.mockReset();
  reload.mockReset();
  vi.stubGlobal('EventSource', FakeStream);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('the waiting room', () => {
  it('shows each queue, and shows the one called highlighted as soon as the queue changes', async () => {
    get.mockResolvedValueOnce([ROMERO, { id: 'q2', name: 'Guardia', room: null, open: false, called: [], waiting: [] }]);
    show();
    const romero = await screen.findByRole('region', { name: 'Dra. Romero' });
    expect(get).toHaveBeenCalledWith('/api/waiting-room');
    expect(FakeStream.last.url).toBe('/queue-events');
    expect(within(romero).getByText('2 esperando')).toBeInTheDocument();
    expect(within(romero).queryByRole('status')).not.toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Guardia' })).getByText('Cerrada')).toBeInTheDocument();

    get.mockResolvedValueOnce([{ ...ROMERO, called: ['Tomás Suárez'], waiting: ['Camila Méndez'] }]);
    act(() => FakeStream.last.emit('queue', 'q1'));

    expect(await within(romero).findByRole('status')).toHaveTextContent('Tomás SuárezPasá al consultorio 2');
    const waiting = within(romero).getByRole('list', { name: 'Esperan en Dra. Romero' });
    expect(within(waiting).getAllByRole('listitem').map((item) => item.textContent)).toEqual(['Camila MéndezSigue']);
    expect(within(romero).getByText('1 esperando')).toBeInTheDocument();
  });

  it('loads itself again when the stream ends for good, so it never shows what it no longer knows', async () => {
    get.mockResolvedValue([ROMERO]);
    const { unmount } = show();
    await screen.findByRole('region', { name: 'Dra. Romero' });

    act(() => FakeStream.last.emit('error'));
    expect(reload).not.toHaveBeenCalled();
    FakeStream.last.readyState = FakeStream.CLOSED;
    act(() => FakeStream.last.emit('error'));
    expect(reload).toHaveBeenCalled();

    unmount();
    expect(FakeStream.last.closed).toBe(true);
  });
});
