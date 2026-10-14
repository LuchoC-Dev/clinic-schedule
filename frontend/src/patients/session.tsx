import { type Dispatch, type SetStateAction, createContext } from 'react';

import { type ApiClient, createApiClient } from '../shared/api';

/** Where this application lives: `/portal/app/` while the screens move over, `/portal/` once they have (D-97). */
export const BASE = import.meta.env.BASE_URL;

/** Reloads the application at its login, so nothing read stays in memory. */
export function leaveFor(login: 'logout' | 'expired') {
  window.location.replace(`${BASE}login?${login}`);
}

export const api: ApiClient = createApiClient({ onSessionEnded: () => leaveFor('expired') });

export interface PatientSession {
  loggedIn: boolean;
  /** The types of document the login offers, as reception types them (D-26). */
  documentTypes: string[];
}

interface SessionBody extends PatientSession {
  csrfToken: string;
}

/** Whether a patient is logged in; it also gets the CSRF token the login and every write carry. */
export async function loadSession(client: ApiClient = api): Promise<PatientSession> {
  const { csrfToken, ...session } = await client.get<SessionBody>('/api/portal/session');
  client.setCsrfToken(csrfToken);
  return session;
}

/**
 * Logs in with the document, as a type and a number, which the server joins (D-80); every failure counts
 * towards the lock, whatever the way in (D-86).
 */
export async function logIn(documentType: string, documentNumber: string, password: string,
    client: ApiClient = api): Promise<PatientSession> {
  // The token the page started with may belong to a session that is gone by now.
  await loadSession(client);
  const { csrfToken, ...session } = await client.logIn<SessionBody>('/api/portal/session',
    { documentType, documentNumber, password });
  client.setCsrfToken(csrfToken);
  return session;
}

/** Ends the whole session, and reloads at the login. */
export async function logOut(client: ApiClient = api) {
  try {
    await client.post('/api/portal/session/logout');
  } finally {
    leaveFor('logout');
  }
}

export const SessionContext = createContext<PatientSession>({ loggedIn: false, documentTypes: [] });

export const SetSessionContext = createContext<Dispatch<SetStateAction<PatientSession>>>(() => {});
