import { describe, expect, it, vi } from 'vitest';

import { ApiError, NetworkError, createApiClient } from './api';

function answering(status: number, body?: unknown) {
  return vi.fn(async () => new Response(body === undefined ? null : JSON.stringify(body), {
    status, headers: { 'Content-Type': 'application/json' },
  }));
}

const conflict = { status: 409, error: 'Conflict', message: 'The practitioner already has an appointment then' };

describe('the API client', () => {
  it('reads an answer, with the query of what is given', async () => {
    const fetch = answering(200, { date: '2026-03-10' });
    const api = createApiClient({ onSessionEnded: vi.fn(), fetch });

    await expect(api.get('/api/agenda', { date: '2026-03-10', practitionerId: null })).resolves
      .toEqual({ date: '2026-03-10' });
    expect(fetch).toHaveBeenCalledWith('/api/agenda?date=2026-03-10', expect.objectContaining({
      method: 'GET', credentials: 'same-origin', cache: 'no-store',
    }));
  });

  it('sends the CSRF token with every write, and never with a read', async () => {
    const fetch = answering(200, {});
    const api = createApiClient({ onSessionEnded: vi.fn(), fetch });
    api.setCsrfToken('el-token');

    await api.get('/api/agenda');
    await api.post('/api/appointments', { reason: 'x' });

    const [, read] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    const [, write] = fetch.mock.calls[1] as unknown as [string, RequestInit];
    expect(read.headers).not.toHaveProperty('X-CSRF-TOKEN');
    expect(write.headers).toMatchObject({ 'X-CSRF-TOKEN': 'el-token', 'Content-Type': 'application/json' });
    expect(write.body).toBe('{"reason":"x"}');
  });

  it('uploads a file as a form with its part named file, with the token and no type of its own', async () => {
    const fetch = answering(201, { id: 'f1' });
    const api = createApiClient({ onSessionEnded: vi.fn(), fetch });
    api.setCsrfToken('el-token');
    const file = new File(['%PDF-1.4'], 'hemograma.pdf', { type: 'application/pdf' });

    await expect(api.upload('/api/checks/c1/attachments', file)).resolves.toEqual({ id: 'f1' });

    const [path, sent] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(path).toBe('/api/checks/c1/attachments');
    expect(sent.method).toBe('POST');
    expect(sent.headers).toMatchObject({ 'X-CSRF-TOKEN': 'el-token' });
    expect(sent.headers).not.toHaveProperty('Content-Type');
    expect((sent.body as FormData).get('file')).toBe(file);
  });

  it("gives a refusal back with the server's body, a 409's too, and the session goes on", async () => {
    const onSessionEnded = vi.fn();
    const api = createApiClient({ onSessionEnded, fetch: answering(409, conflict) });

    const refusal = await api.post('/api/appointments', {}).catch((e: unknown) => e);

    expect(refusal).toBeInstanceOf(ApiError);
    expect((refusal as ApiError).status).toBe(409);
    expect((refusal as ApiError).body).toEqual(conflict);
    expect(onSessionEnded).not.toHaveBeenCalled();
  });

  it('ends the session on a 401', async () => {
    const onSessionEnded = vi.fn();
    const api = createApiClient({ onSessionEnded, fetch: answering(401, { status: 401, error: 'x', message: 'y' }) });

    await expect(api.get('/api/agenda')).rejects.toBeInstanceOf(ApiError);
    expect(onSessionEnded).toHaveBeenCalledOnce();
  });

  it("answers a wrong login as a refusal, without ending a session it never had, as a form Spring reads", async () => {
    const onSessionEnded = vi.fn();
    const fetch = answering(401, { status: 401, error: 'Unauthenticated', message: 'Wrong username or password' });
    const api = createApiClient({ onSessionEnded, fetch });
    api.setCsrfToken('el-token');

    await expect(api.logIn('/api/session', { username: 'ana', password: 'x y' })).rejects.toMatchObject({ status: 401 });
    expect(onSessionEnded).not.toHaveBeenCalled();
    const [, login] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(login.body).toBe('username=ana&password=x+y');
    expect(login.headers).toMatchObject({
      'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': 'el-token',
    });
  });

  it('tells a server it could not reach apart from one that said no', async () => {
    const api = createApiClient({ onSessionEnded: vi.fn(), fetch: vi.fn(async () => { throw new TypeError('down'); }) });

    await expect(api.get('/api/agenda')).rejects.toBeInstanceOf(NetworkError);
  });

  it("keeps an error without a body of its own, or with one that is not the API's", async () => {
    const page = createApiClient({ onSessionEnded: vi.fn(), fetch: vi.fn(async () => new Response('<html>', { status: 502 })) });
    const other = createApiClient({ onSessionEnded: vi.fn(), fetch: answering(500, { reason: 'x' }) });

    const refusal = await page.get('/api/agenda').catch((e: unknown) => e) as ApiError;
    expect(refusal.status).toBe(502);
    expect(refusal.body).toBeNull();
    await expect(other.get('/api/agenda')).rejects.toMatchObject({ status: 500, body: null });
  });
});
