import { QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useContext, useMemo, useState } from 'react';
import { Navigate, Outlet, type RouteObject, RouterProvider, createBrowserRouter } from 'react-router';

import { newQueryClient } from '../shared/queries';
import { Theme } from '../shared/theme';
import { AgendaPage } from './agenda/AgendaPage';
import { Layout } from './Layout';
import { LoginPage } from './LoginPage';
import { GoTo } from './GoTo';
import { type Role, type StaffUser, landingFor } from './navigation';
import { BASE, SessionContext, SetUserContext, useUser } from './session';

function RequireUser({ children }: { children: ReactNode }) {
  return useContext(SessionContext) ? children : <Navigate to="/login" replace />;
}

/** A screen of one role: anyone else, and anyone who must change their password, goes where they land. */
function OnlyFor({ role, children }: { role: Role; children: ReactNode }) {
  const user = useUser();
  return user.role === role && !user.temporaryPassword ? children : <GoTo entry={landingFor(user)} />;
}

function Landing() {
  return <GoTo entry={landingFor(useUser())} />;
}

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireUser><Layout><Outlet /></Layout></RequireUser>,
    children: [
      { index: true, element: <Landing /> },
      { path: 'agenda', element: <OnlyFor role="RECEPTION"><AgendaPage /></OnlyFor> },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
];

export function App({ initialUser }: { initialUser: StaffUser | null }) {
  const [user, setUser] = useState(initialUser);
  const router = useMemo(() => createBrowserRouter(routes, { basename: BASE.replace(/\/$/, '') || '/' }), []);
  const queries = useMemo(() => newQueryClient(), []);
  return (
    <Theme>
      <QueryClientProvider client={queries}>
        <SessionContext value={user}>
          <SetUserContext value={setUser}>
            <RouterProvider router={router} />
          </SetUserContext>
        </SessionContext>
      </QueryClientProvider>
    </Theme>
  );
}
