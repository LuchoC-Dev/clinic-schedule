import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../shared/api';
import { newQueryClient } from '../shared/queries';
import { Theme } from '../shared/theme';
import { type Copies } from './copies/copies';
import { RecordCopiesPage } from './copies/RecordCopiesPage';
import { Layout } from './Layout';
import { type Profile } from './profile/profile';
import { ProfilePage } from './profile/ProfilePage';
import { api, SessionContext } from './session';
import type { Group } from './family/family';
import { FamilyPage } from './family/FamilyPage';

vi.mock('./session', async (original) => ({
  ...(await original<typeof import('./session')>()),
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
}));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const put = vi.mocked(api.put);

const refusal = (status: number, code?: string) =>
  new ApiError(status, { status, error: 'Refused', message: 'in English', code });

function show(address: string) {
  render(
    <Theme>
      <QueryClientProvider client={newQueryClient()}>
        <SessionContext value={{ loggedIn: true, documentTypes: ['DNI', 'PASAPORTE'] }}>
          <MemoryRouter initialEntries={[address]}>
            <Routes>
              <Route path="/family" element={<FamilyPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/record-copies" element={<RecordCopiesPage />} />
              <Route path="/layout" element={<Layout><p>pantalla</p></Layout>} />
            </Routes>
          </MemoryRouter>
        </SessionContext>
      </QueryClientProvider>
    </Theme>,
  );
}

function answer(paths: Record<string, unknown>) {
  get.mockImplementation(((path: string) => (path in paths ? Promise.resolve(paths[path])
    : Promise.reject(new Error(path)))) as typeof api.get);
}

const PROFILE: Profile = {
  fullName: 'Laura Medina', document: 'DNI 27333444', birthDate: '1990-05-04', ageBand: 'ADULT',
  phone: { value: '1155667788', owner: { name: 'Marta', relation: 'MOTHER' }, consent: 'OWNER' },
  email: { value: 'laura@mail.com', owner: null, consent: null }, insurance: null,
};

const GROUPS: Group[] = [
  { id: 'g1', name: 'Familia Medina', members: [
    { fullName: 'Laura Medina', age: 'ADULT', shares: false, me: true },
    { fullName: 'Benjamín Medina', age: 'CHILD', shares: false, me: false }] },
  { id: 'g2', name: 'Familia Ruiz', members: [{ fullName: 'Laura Medina', age: 'ADULT', shares: true, me: true }] },
];

beforeEach(() => {
  get.mockReset();
  post.mockReset();
  put.mockReset();
});

describe('the bar at the bottom', () => {
  it("leads to every section of this application", () => {
    show('/layout');
    const bar = screen.getByRole('navigation', { name: 'Secciones' });
    expect(within(bar).getAllByRole('link').map((link) => link.getAttribute('href')))
      .toEqual(['/appointments', '/family', '/record-copies', '/profile']);
  });
});

describe('Mi familia', () => {
  it("lists each group's members, and shares or stops sharing the patient's own", async () => {
    answer({ '/api/portal/family': GROUPS, '/api/portal/profile': PROFILE });
    post.mockResolvedValue(GROUPS);
    show('/family');

    const medina = await screen.findByRole('region', { name: 'Familia Medina' });
    expect(medina).toHaveTextContent('Laura Medina (vos)');
    expect(medina).toHaveTextContent('Los comparte siempre (menor de 13)');
    await userEvent.click(within(medina).getByRole('button', { name: 'Compartir mis turnos' }));
    expect(post).toHaveBeenCalledWith('/api/portal/family/g1/sharing', { shares: true });
    expect(await screen.findByRole('status')).toHaveTextContent('Listo: tu familia ahora ve tus turnos.');

    await userEvent.click(within(screen.getByRole('region', { name: 'Familia Ruiz' }))
      .getByRole('button', { name: 'Dejar de compartir mis turnos' }));
    expect(post).toHaveBeenLastCalledWith('/api/portal/family/g2/sharing', { shares: false });
    expect(await screen.findByText('Listo: tus turnos ya no los ve tu familia.')).toBeInTheDocument();
  });

  it('leaves a group, and says one left meanwhile', async () => {
    answer({ '/api/portal/family': GROUPS, '/api/portal/profile': PROFILE });
    post.mockResolvedValueOnce(GROUPS).mockRejectedValueOnce(refusal(404, 'FAMILY_GROUP_NOT_FOUND'));
    show('/family');

    const ruiz = await screen.findByRole('region', { name: 'Familia Ruiz' });
    await userEvent.click(within(ruiz).getByRole('button', { name: 'Salir del grupo' }));
    expect(post).toHaveBeenCalledWith('/api/portal/family/g2/leave');
    expect(await screen.findByRole('status')).toHaveTextContent('Saliste del grupo.');
    await userEvent.click(within(ruiz).getByRole('button', { name: 'Salir del grupo' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Ya no estás en ese grupo.');
  });

  it('lets an adult register a child, in their first group unless they choose', async () => {
    answer({ '/api/portal/family': GROUPS, '/api/portal/profile': PROFILE });
    post.mockResolvedValue(undefined);
    show('/family');

    const form = await screen.findByRole('region', { name: 'Registrar a un hijo menor de 13' });
    expect(within(form).getByLabelText('En el grupo')).toHaveValue('g1');
    await userEvent.type(within(form).getByLabelText(/Nombre completo/), 'Tomás Medina');
    await userEvent.type(within(form).getByLabelText(/Número/), '55111222');
    await userEvent.type(within(form).getByLabelText(/Fecha de nacimiento/), '2019-02-03');
    await userEvent.selectOptions(within(form).getByLabelText('Sos su'), 'FATHER');
    await userEvent.click(within(form).getByRole('button', { name: 'Registrar' }));

    expect(post).toHaveBeenCalledWith('/api/portal/family/children', { fullName: 'Tomás Medina', documentType: 'DNI',
      documentNumber: '55111222', birthDate: '2019-02-03', relation: 'FATHER', groupId: 'g1' });
    expect(await screen.findByRole('status')).toHaveTextContent('Si Tomás Medina no aparece en tu familia');
    expect(within(form).getByLabelText(/Nombre completo/)).toHaveValue('');

    await userEvent.type(within(form).getByLabelText(/Nombre completo/), 'Ema');
    await userEvent.type(within(form).getByLabelText(/Número/), '55111223');
    await userEvent.type(within(form).getByLabelText(/Fecha de nacimiento/), '2019-02-03');
    await userEvent.selectOptions(within(form).getByLabelText('En el grupo'), '');
    await userEvent.click(within(form).getByRole('button', { name: 'Registrar' }));
    expect(post).toHaveBeenLastCalledWith('/api/portal/family/children', expect.objectContaining({ groupId: null }));
  });

  it('offers registering a child to an adult alone, and says when there is no group', async () => {
    answer({ '/api/portal/family': [], '/api/portal/profile': { ...PROFILE, ageBand: 'ADOLESCENT' } });
    show('/family');
    expect(await screen.findByText(/No estás en ningún grupo familiar/)).toBeInTheDocument();
    await screen.findByText('Mi familia');
    expect(screen.queryByRole('region', { name: 'Registrar a un hijo menor de 13' })).not.toBeInTheDocument();
  });
});

describe('Mis datos', () => {
  it('shows who the patient is, and saves their contact as typed', async () => {
    answer({ '/api/portal/profile': PROFILE });
    // The server keeps the phone as its own number, without the owner it had.
    put.mockResolvedValue({ ...PROFILE, phone: { value: '1155667788', owner: null, consent: null },
      insurance: { insurer: 'OSDE', memberNumber: null, plan: null } });
    show('/profile');

    expect(await screen.findByText(/DNI 27333444/)).toHaveTextContent('Laura Medina · DNI 27333444 · nacimiento 4/05/1990');
    await userEvent.type(screen.getByLabelText('Obra social o prepaga'), 'OSDE');
    await userEvent.click(screen.getAllByRole('button', { name: 'Guardar' })[0]);

    expect(put).toHaveBeenCalledWith('/api/portal/profile/details', {
      phone: { number: '1155667788', owner: { name: 'Marta', relation: 'MOTHER' } },
      email: { address: 'laura@mail.com', owner: null },
      insurance: { insurer: 'OSDE', memberNumber: '', plan: '' },
    });
    expect(await screen.findByRole('status')).toHaveTextContent('Tus datos quedaron guardados.');
    expect(screen.getByLabelText('Obra social o prepaga')).toHaveValue('OSDE');
    expect(screen.getByLabelText('El teléfono es de')).toHaveValue('');
    expect(screen.getByLabelText('Su nombre, si el teléfono no es tuyo')).toHaveValue('');
    expect(screen.getByLabelText('Al teléfono 1155667788')).toHaveValue('false');
    // Its consent was the owner's, and went with them: saving untouched now agrees to nothing.
    await userEvent.click(screen.getAllByRole('button', { name: 'Guardar' })[1]);
    expect(put).toHaveBeenLastCalledWith('/api/portal/profile/contact-consent', { phone: false, email: false });
  });

  it("says a refusal and keeps what was typed", async () => {
    answer({ '/api/portal/profile': PROFILE });
    put.mockRejectedValue(refusal(400, 'OWN_DATA_REFUSED'));
    show('/profile');

    await userEvent.type(await screen.findByLabelText('Obra social o prepaga'), 'X');
    await userEvent.click(screen.getAllByRole('button', { name: 'Guardar' })[0]);
    expect(await screen.findByRole('alert')).toHaveTextContent('el teléfono lleva de 8 a 15 dígitos');
    expect(screen.getByLabelText('Obra social o prepaga')).toHaveValue('X');
  });

  it("saves the reminders of each channel as chosen, the owner's consent kept as it stands", async () => {
    answer({ '/api/portal/profile': PROFILE });
    put.mockResolvedValue(PROFILE);
    show('/profile');

    expect(await screen.findByLabelText('Al teléfono 1155667788')).toHaveValue('');
    expect(screen.getByLabelText('Al email laura@mail.com')).toHaveValue('false');
    await userEvent.selectOptions(screen.getByLabelText('Al email laura@mail.com'), 'true');
    await userEvent.click(screen.getAllByRole('button', { name: 'Guardar' })[1]);

    expect(put).toHaveBeenCalledWith('/api/portal/profile/contact-consent', { phone: null, email: true });
    expect(await screen.findByRole('status')).toHaveTextContent('Tus recordatorios quedaron guardados.');
  });

  it('asks for a phone or an email before any reminder, and leads to changing the password', async () => {
    answer({ '/api/portal/profile': { ...PROFILE, phone: null, email: null } });
    show('/profile');
    expect(await screen.findByText('Cargá un teléfono o un email para recibir recordatorios.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Cambiar tu contraseña' })).toHaveAttribute('href', '/password');
  });
});

const COPIES: Copies = {
  askableFor: [{ id: 'me', fullName: 'Laura Medina' }, { id: 'k1', fullName: 'Benjamín Medina' }],
  copies: [
    { id: 'c1', patient: { id: 'me', fullName: 'Laura Medina' }, requestedAt: '2026-03-01T09:30:00', delivery: 'PDF',
      state: 'READY_TO_DOWNLOAD', deadline: '2026-03-03T09:30:00', authenticatedAt: '2026-03-02T10:00:00',
      handedOverAt: null, own: true },
    { id: 'c2', patient: { id: 'k1', fullName: 'Benjamín Medina' }, requestedAt: '2026-03-09T08:00:00',
      delivery: 'PAPER', state: 'WAITING', deadline: '2026-03-11T08:00:00', authenticatedAt: null, handedOverAt: null,
      own: false },
  ],
};

describe('Mi historia clínica', () => {
  it('lists each copy, and downloads only one ready with a plain link', async () => {
    answer({ '/api/portal/record-copies': COPIES });
    show('/record-copies');

    const mine = await screen.findByRole('article', { name: 'Tu historia, 01/03/2026 09:30' });
    expect(within(mine).getByRole('link', { name: 'Descargar PDF' }))
      .toHaveAttribute('href', '/api/portal/record-copies/c1/pdf');
    const his = screen.getByRole('article', { name: 'Historia de Benjamín Medina, 09/03/2026 08:00' });
    expect(his).toHaveTextContent('a más tardar el 11/03/2026 08:00');
    expect(within(his).queryByRole('link')).not.toBeInTheDocument();
  });

  it('asks for one of whom is chosen, once how it is delivered is chosen', async () => {
    answer({ '/api/portal/record-copies': COPIES });
    post.mockRejectedValueOnce(refusal(409, 'RECORD_COPY_WAITING')).mockResolvedValueOnce({});
    show('/record-copies');

    await userEvent.click(await screen.findByRole('button', { name: 'Pedir copia' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Elegí cómo querés recibir la copia.');
    expect(post).not.toHaveBeenCalled();

    await userEvent.selectOptions(screen.getByLabelText('De quién'), 'k1');
    await userEvent.click(screen.getByLabelText(/En papel/));
    await userEvent.click(screen.getByRole('button', { name: 'Pedir copia' }));
    expect(post).toHaveBeenCalledWith('/api/portal/record-copies', { patientId: 'k1', delivery: 'PAPER' });
    expect(await screen.findByRole('alert')).toHaveTextContent('Ya hay una copia pedida de esa historia');

    await userEvent.selectOptions(screen.getByLabelText('De quién'), 'me');
    await userEvent.click(screen.getByLabelText(/En PDF/));
    await userEvent.click(screen.getByRole('button', { name: 'Pedir copia' }));
    expect(post).toHaveBeenLastCalledWith('/api/portal/record-copies', { patientId: 'me', delivery: 'PDF' });
    expect(await screen.findByRole('status')).toHaveTextContent('Pedimos la copia.');
  });

  it('asks nobody whose when there is only the patient', async () => {
    answer({ '/api/portal/record-copies': { askableFor: [COPIES.askableFor[0]], copies: [] } });
    show('/record-copies');
    expect(await screen.findByText('Todavía no pediste ninguna copia.')).toBeInTheDocument();
    expect(screen.queryByLabelText('De quién')).not.toBeInTheDocument();
  });

  it('says who may ask for none to go to reception', async () => {
    answer({ '/api/portal/record-copies': { askableFor: [], copies: [] } });
    show('/record-copies');
    expect(await screen.findByText('Para pedir una copia desde la web, acercate a recepción.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pedir copia' })).not.toBeInTheDocument();
  });
});
