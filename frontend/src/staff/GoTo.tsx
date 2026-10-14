import { useEffect } from 'react';
import { Navigate } from 'react-router';

import type { MenuEntry } from './navigation';

/** Sends the user where an entry points: within this application, or to a Thymeleaf screen. */
export function GoTo({ entry }: { entry: MenuEntry }) {
  useEffect(() => {
    if (!entry.inApp) {
      window.location.replace(entry.to);
    }
  }, [entry.inApp, entry.to]);
  return entry.inApp ? <Navigate to={entry.to} replace /> : null;
}
