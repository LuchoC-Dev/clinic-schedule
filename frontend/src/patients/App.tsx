import { QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useContext, useMemo, useState } from 'react';
import { Navigate, Outlet, type RouteObject, RouterProvider, createBrowserRouter } from 'react-router';

import { Brand } from '../shared/Brand';
import { newQueryClient } from '../shared/queries';
import { Theme } from '../shared/theme';
import { ActivationPage, PasswordResetPage } from './account/LinkPages';
import { PasswordPage, PasswordRecoveryPage } from './account/PasswordPages';
import { CancelLinkPage } from './cancel/CancelLinkPage';
import { RecordCopiesPage } from './copies/RecordCopiesPage';
import { FamilyPage } from './family/FamilyPage';
import { AppointmentsPage } from './appointments/AppointmentsPage';
import { BookingPage } from './appointments/BookingPage';
import { MovePage } from './appointments/MovePage';
import { Layout } from './Layout';
import { LoginPage } from './LoginPage';
import { ProfilePage } from './profile/ProfilePage';
import { RegisterConfirmPage, RegisterPage, RegisterSentPage } from './register/RegisterPages';
import { BASE, type PatientSession, SessionContext, SetSessionContext } from './session';

function RequirePatient({ children }: { children: ReactNode }) {
  return useContext(SessionContext).loggedIn ? children : <Navigate to="/login" replace />;
}

/**
 * The patients' addresses, under `/portal/`, are those the emails carry, sent ones included — `/portal/activation`,
 * `/portal/password-reset`, `/portal/register/confirm`, `/portal/cancel`, `/portal/appointments` — (D-110, D-114).
 * Activating, a new password, registering and cancelling from a link need nobody logged in.
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
      // A patient lands on their appointments (D-80).
      { index: true, element: <Navigate to="/appointments" replace /> },
      { path: '/appointments', element: <AppointmentsPage /> },
      { path: '/appointments/:id/move', element: <MovePage /> },
      { path: '/booking', element: <BookingPage /> },
      { path: '/family', element: <FamilyPage /> },
      { path: '/record-copies', element: <RecordCopiesPage /> },
      { path: '/profile', element: <ProfilePage /> },
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
