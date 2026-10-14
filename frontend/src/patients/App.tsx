import { Anchor, Stack, Text, Title } from '@mantine/core';
import { type ReactNode, useContext, useMemo, useState } from 'react';
import { Navigate, Outlet, type RouteObject, RouterProvider, createBrowserRouter } from 'react-router';

import { Brand } from '../shared/Brand';
import { Theme } from '../shared/theme';
import { Layout, TABS } from './Layout';
import { LoginPage } from './LoginPage';
import { BASE, type PatientSession, SessionContext, SetSessionContext } from './session';

function RequirePatient({ children }: { children: ReactNode }) {
  return useContext(SessionContext).loggedIn ? children : <Navigate to="/login" replace />;
}

/** Where a patient lands until their screens move here, in part 5: their sections. */
function Home() {
  return (
    <Stack gap={12}>
      <Title order={1} fz={26}>Hola</Title>
      <Text>Elegí qué querés ver:</Text>
      {TABS.map((tab) => <Anchor key={tab.to} href={tab.to} fz={18}>{tab.label}</Anchor>)}
    </Stack>
  );
}

export const routes: RouteObject[] = [
  { path: '/login', element: <Layoutless><LoginPage /></Layoutless> },
  {
    element: <RequirePatient><Layout><Outlet /></Layout></RequirePatient>,
    children: [{ index: true, element: <Home /> }],
  },
  { path: '*', element: <Navigate to="/" replace /> },
];

/** Before logging in there are no sections to offer: only the practice. */
function Layoutless({ children }: { children: ReactNode }) {
  return (
    <div className="patients">
      <header className="patients-header"><Brand size={17} /></header>
      {children}
    </div>
  );
}

export function App({ initialSession }: { initialSession: PatientSession | null }) {
  const [session, setSession] = useState<PatientSession>(initialSession ?? { loggedIn: false, documentTypes: [] });
  const router = useMemo(() => createBrowserRouter(routes, { basename: BASE.replace(/\/$/, '') || '/' }), []);
  return (
    <Theme>
      <SessionContext value={session}>
        <SetSessionContext value={setSession}>
          <RouterProvider router={router} />
        </SetSessionContext>
      </SessionContext>
    </Theme>
  );
}
