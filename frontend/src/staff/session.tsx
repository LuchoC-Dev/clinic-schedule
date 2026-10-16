import { type Dispatch, type SetStateAction, createContext, useContext } from 'react';

import { type ApiClient, createApiClient } from '../shared/api';
import type { StaffUser } from './navigation';

/** Where this application lives: `/` (D-114). */
export const BASE = import.meta.env.BASE_URL;

/** Reloads the application at its login, so nothing read stays in memory. */
export function leaveFor(login: 'logout' | 'expired' | 'changed') {
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

/**
 * Logs in with the API's session login, answered in JSON; the session's id and token are new afterwards. The token
 * is asked for again first: the one the page started with may belong to a session that is gone by now.
 */
export async function logIn(username: string, password: string, client: ApiClient = api): Promise<StaffUser> {
  await loadSession(client);
  const session = await client.logIn<SessionBody>('/api/session', { username, password });
  client.setCsrfToken(session.csrfToken);
  return session.user as StaffUser;
}

/**
 * Ends the whole session and reloads at the login, which says why: logged out, or the
 * password changed.
 */
export async function logOut(client: ApiClient = api, why: 'logout' | 'changed' = 'logout') {
  try {
    await client.post('/api/session/logout');
  } finally {
    leaveFor(why);
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
