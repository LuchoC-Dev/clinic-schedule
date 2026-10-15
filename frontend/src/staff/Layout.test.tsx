import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { newQueryClient } from '../shared/queries';
import { Theme } from '../shared/theme';
import { Layout } from './Layout';

vi.mock('./session', () => ({
  api: { get: vi.fn() }, logOut: vi.fn(), BASE: '/',
  useUser: () => ({ username: 'recepcion', role: 'RECEPTION', practitionerId: null, temporaryPassword: false }),
}));

describe("the staff's menu", () => {
  it("opens the waiting room's screen of this application in a tab of its own, and the rest in the same", () => {
    render(
      <Theme>
        <QueryClientProvider client={newQueryClient()}>
          <MemoryRouter><Layout><p>pantalla</p></Layout></MemoryRouter>
        </QueryClientProvider>
      </Theme>,
    );

    const room = screen.getByRole('link', { name: 'Sala de espera' });
    expect(room).toHaveAttribute('href', '/waiting-room');
    expect(room).toHaveAttribute('target', '_blank');
    expect(screen.getByRole('link', { name: 'Agenda' })).not.toHaveAttribute('target');
  });
});
