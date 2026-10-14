import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './App';
import { loadSession } from './session';

/** Whether a patient is logged in is known before anything is shown, so no screen flashes the login first. */
const session = await loadSession().catch(() => null);
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App initialSession={session} />
  </StrictMode>,
);
