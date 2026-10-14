import { describe, expect, it, vi } from 'vitest';

import type { ApiClient } from '../shared/api';
import { logIn as patientLogIn } from '../patients/session';
import { logIn } from './session';

/** A client that says which token each request would carry. */
function client(sessionPath: string, body: object) {
  let token: string | null = 'el-de-la-pagina';
  const sent: string[] = [];
  const fake: ApiClient = {
    get: vi.fn(async (path: string) => {
      sent.push(`GET ${path}`);
      return { ...body, csrfToken: 'uno-nuevo' };
    }) as ApiClient['get'],
    post: vi.fn() as ApiClient['post'],
    logIn: vi.fn(async (path: string) => {
      sent.push(`POST ${path} with ${token}`);
      return { ...body, csrfToken: 'el-del-login' };
    }) as ApiClient['logIn'],
    setCsrfToken: (next) => {
      token = next;
    },
  };
  return { fake, sent, token: () => token, sessionPath };
}

/** The page may have been open since before its session ended: the login asks for a token of its own first. */
describe('logging in', () => {
  it("asks the staff's session for a new token before sending the login, and keeps the login's", async () => {
    const { fake, sent, token } = client('/api/session', { user: { username: 'recepcion' } });

    await logIn('recepcion', 'recepcion123', fake);

    expect(sent).toEqual(['GET /api/session', 'POST /api/session with uno-nuevo']);
    expect(token()).toBe('el-del-login');
  });

  it("asks the patients' session for a new token before sending the login, and keeps the login's", async () => {
    const { fake, sent, token } = client('/api/portal/session', { loggedIn: true, documentTypes: ['DNI'] });

    await patientLogIn('DNI', '30111222', 'una-clave-larga', fake);

    expect(sent).toEqual(['GET /api/portal/session', 'POST /api/portal/session with uno-nuevo']);
    expect(token()).toBe('el-del-login');
  });
});
