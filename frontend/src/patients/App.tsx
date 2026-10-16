import { Anchor, Stack, Text, Title } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useContext, useMemo, useState } from 'react';
import { Link, Navigate, Outlet, type RouteObject, RouterProvider, createBrowserRouter } from 'react-router';

import { Brand } from '../shared/Brand';
import { newQueryClient } from '../shared/queries';
import { Theme } from '../shared/theme';
import { ActivationPage, PasswordResetPage } from './account/LinkPages';
import { PasswordPage, PasswordRecoveryPage } from './account/PasswordPages';
import { CancelLinkPage } from './cancel/CancelLinkPage';
import { Layout, TABS } from './Layout';
import { LoginPage } from './LoginPage';
import { RegisterConfirmPage, RegisterPage, RegisterSentPage } from './register/RegisterPages';
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
      <Anchor component={Link} to="/password" fz={18}>Contraseña</Anchor>
    </Stack>
  );
}

/**
 * The patients' addresses are the Thymeleaf screens' own, without `/portal`, so the emails' links open the same
 * paths once this application is at `/portal/` (D-97, D-110). Activating, a new password, registering and cancelling
 * from a link need nobody logged in.
 */
export const routes: RouteObject[] = [
  {
    element: <Layoutless><Outlet /></Layoutless>,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/activation', element: <ActivationPage /> },
      { path: '/password-recovery', element: <PasswordRecoveryPage /> },
      { path: '/password-reset', element: <PasswordResetPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/register/sent', element: <RegisterSentPage /> },
      { path: '/register/confirm', element: <RegisterConfirmPage /> },
      { path: '/cancel', element: <CancelLinkPage /> },
    ],
  },
  {
    element: <RequirePatient><Layout><Outlet /></Layout></RequirePatient>,
    children: [
      { index: true, element: <Home /> },
      { path: '/password', element: <PasswordPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
];

/** Before logging in there are no sections to offer: only the practice. */
function Layoutless({ children }: { children: ReactNode }) {
  return (
    <div className="patients">
      <header className="patients-header"><Brand size={17} /></header>
      <main className="patients-main">{children}</main>
    </div>
  );
}

export function App({ initialSession }: { initialSession: PatientSession | null }) {
  const [session, setSession] = useState<PatientSession>(initialSession ?? { loggedIn: false, documentTypes: [] });
  const router = useMemo(() => createBrowserRouter(routes, { basename: BASE.replace(/\/$/, '') || '/' }), []);
  const queries = useMemo(() => newQueryClient(), []);
  return (
    <Theme>
      <QueryClientProvider client={queries}>
        <SessionContext value={session}>
          <SetSessionContext value={setSession}>
            <RouterProvider router={router} />
          </SetSessionContext>
        </SessionContext>
      </QueryClientProvider>
    </Theme>
  );
}
