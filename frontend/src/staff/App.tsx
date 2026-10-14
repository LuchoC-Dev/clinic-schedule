import { QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useContext, useMemo, useState } from 'react';
import { Navigate, Outlet, type RouteObject, RouterProvider, createBrowserRouter } from 'react-router';

import { newQueryClient } from '../shared/queries';
import { Theme } from '../shared/theme';
import { AccountPage } from './account/AccountPage';
import { AgendaPage } from './agenda/AgendaPage';
import { ArrivalsPage } from './arrivals/ArrivalsPage';
import { CashDeskPage } from './cash/CashDeskPage';
import { FamilyGroupPage } from './family/FamilyGroupPage';
import { FamilyGroupsPage } from './family/FamilyGroupsPage';
import { BookingPage } from './booking/BookingPage';
import { MovePage } from './booking/MovePage';
import { PatientAppointmentsPage } from './patients/PatientAppointmentsPage';
import { PatientPage } from './patients/PatientPage';
import { PatientsPage } from './patients/PatientsPage';
import { Layout } from './Layout';
import { LoginPage } from './LoginPage';
import { CancellationsPage } from './logs/CancellationsPage';
import { RemindersPage } from './logs/RemindersPage';
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
      { path: 'agenda/new', element: <OnlyFor role="RECEPTION"><BookingPage /></OnlyFor> },
      { path: 'agenda/appointments/:id/move', element: <OnlyFor role="RECEPTION"><MovePage /></OnlyFor> },
      { path: 'patients', element: <OnlyFor role="RECEPTION"><PatientsPage /></OnlyFor> },
      { path: 'patients/:id', element: <OnlyFor role="RECEPTION"><PatientPage /></OnlyFor> },
      { path: 'patients/:id/appointments', element: <OnlyFor role="RECEPTION"><PatientAppointmentsPage /></OnlyFor> },
      { path: 'patients/:id/account', element: <OnlyFor role="RECEPTION"><AccountPage /></OnlyFor> },
      { path: 'arrivals', element: <OnlyFor role="RECEPTION"><ArrivalsPage /></OnlyFor> },
      { path: 'family-groups', element: <OnlyFor role="RECEPTION"><FamilyGroupsPage /></OnlyFor> },
      { path: 'family-groups/:id', element: <OnlyFor role="RECEPTION"><FamilyGroupPage /></OnlyFor> },
      { path: 'cash-desk', element: <OnlyFor role="RECEPTION"><CashDeskPage /></OnlyFor> },
      { path: 'cancellations', element: <OnlyFor role="RECEPTION"><CancellationsPage /></OnlyFor> },
      { path: 'reminders', element: <OnlyFor role="RECEPTION"><RemindersPage /></OnlyFor> },
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
