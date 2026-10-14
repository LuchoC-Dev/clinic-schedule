import { type Dispatch, type SetStateAction, createContext, useContext } from 'react';

import { type ApiClient, createApiClient } from '../shared/api';
import type { StaffUser } from './navigation';

/** Where this application lives: `/app/` while the screens move over, `/` once they have (D-97). */
export const BASE = import.meta.env.BASE_URL;

/** Reloads the application at its login, so nothing read stays in memory. */
export function leaveFor(login: 'logout' | 'expired') {
  window.location.replace(`${BASE}login?${login}`);
}

export const api: ApiClient = createApiClient({ onSessionEnded: () => leaveFor('expired') });

interface SessionBody {
  user: StaffUser | null;
  csrfToken: string;
}

/** Who is logged in, if anyone; it also gets the CSRF token the login and every write carry. */
export async function loadSession(client: ApiClient = api): Promise<StaffUser | null> {
  const session = await client.get<SessionBody>('/api/session');
  client.setCsrfToken(session.csrfToken);
  return session.user;
}

/** Logs in with the screens' own login, answered in JSON; the session's id and token are new afterwards. */
export async function logIn(username: string, password: string, client: ApiClient = api): Promise<StaffUser> {
  const session = await client.logIn<SessionBody>('/api/session', { username, password });
  client.setCsrfToken(session.csrfToken);
  return session.user as StaffUser;
}

/** Ends the whole session, the Thymeleaf screens' too, and reloads at the login. */
export async function logOut(client: ApiClient = api) {
  try {
    await client.post('/api/session/logout');
  } finally {
    leaveFor('logout');
  }
}

export const SessionContext = createContext<StaffUser | null>(null);

/** Set by the login, once it went through. */
export const SetUserContext = createContext<Dispatch<SetStateAction<StaffUser | null>>>(() => {});

/** The user logged in, inside the screens that need one. */
export function useUser(): StaffUser {
  const user = useContext(SessionContext);
  if (!user) {
    throw new Error('useUser outside a logged-in screen');
  }
  return user;
}
