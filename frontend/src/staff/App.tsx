import { QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useContext, useMemo, useState } from 'react';
import { Navigate, Outlet, type RouteObject, RouterProvider, createBrowserRouter } from 'react-router';

import { newQueryClient } from '../shared/queries';
import { Theme } from '../shared/theme';
import { AccountPage } from './account/AccountPage';
import { CancellationLogPage, CashDeskLogPage, ReadingsPage, RefusalsPage } from './admin/LogPages';
import { UserPage } from './admin/UserPage';
import { UsersPage } from './admin/UsersPage';
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
import { PractitionerPage } from './setup/PractitionerPage';
import { PractitionersPage } from './setup/PractitionersPage';
import { QueuePage, QueuesPage } from './setup/QueuePages';
import { MyAgendaPage } from './agenda/MyAgendaPage';
import { MyOrdersPage } from './checks/MyOrdersPage';
import { EnvelopesPage } from './copies/EnvelopesPage';
import { RecordCopiesPage } from './copies/RecordCopiesPage';
import { RecordCopyPage } from './copies/RecordCopyPage';
import { PendingChecksPage } from './checks/PendingChecksPage';
import { MyQueuePage } from './queue/MyQueuePage';
import { WaitingRoomPage } from './room/WaitingRoomPage';
import { RecordPage } from './record/RecordPage';
import { type Role, type StaffUser, landingFor } from './navigation';
import { BASE, SessionContext, SetUserContext, useUser } from './session';

function RequireUser({ children }: { children: ReactNode }) {
  return useContext(SessionContext) ? children : <Navigate to="/login" replace />;
}

/** A screen of one role, or a few: anyone else, and anyone who must change their password, goes where they land. */
function OnlyFor({ role, children }: { role: Role | Role[]; children: ReactNode }) {
  const user = useUser();
  const allowed = Array.isArray(role) ? role.includes(user.role) : user.role === role;
  return allowed && !user.temporaryPassword ? children : <GoTo entry={landingFor(user)} />;
}

function Landing() {
  return <GoTo entry={landingFor(useUser())} />;
}

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  // The waiting room's screen, on the wall: full screen, without the header and the menu (D-109).
  {
    path: '/waiting-room',
    element: <RequireUser><OnlyFor role={['RECEPTION', 'WAITING_ROOM']}><WaitingRoomPage /></OnlyFor></RequireUser>,
  },
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
      { path: 'practitioners', element: <OnlyFor role="RECEPTION"><PractitionersPage /></OnlyFor> },
      { path: 'practitioners/:id', element: <OnlyFor role="RECEPTION"><PractitionerPage /></OnlyFor> },
      { path: 'queues', element: <OnlyFor role="RECEPTION"><QueuesPage /></OnlyFor> },
      { path: 'queues/:id', element: <OnlyFor role="RECEPTION"><QueuePage /></OnlyFor> },
      { path: 'record-copy-envelopes', element: <OnlyFor role="RECEPTION"><EnvelopesPage /></OnlyFor> },
      { path: 'my-queue', element: <OnlyFor role="PRACTITIONER"><MyQueuePage /></OnlyFor> },
      { path: 'my-agenda', element: <OnlyFor role="PRACTITIONER"><MyAgendaPage /></OnlyFor> },
      { path: 'clinical-records/:patientId', element: <OnlyFor role="PRACTITIONER"><RecordPage /></OnlyFor> },
      { path: 'checks/pending', element: <OnlyFor role="PRACTITIONER"><PendingChecksPage /></OnlyFor> },
      { path: 'orders/mine', element: <OnlyFor role="PRACTITIONER"><MyOrdersPage /></OnlyFor> },
      { path: 'record-copies', element: <OnlyFor role="PRACTITIONER"><RecordCopiesPage /></OnlyFor> },
      { path: 'record-copies/:id', element: <OnlyFor role="PRACTITIONER"><RecordCopyPage /></OnlyFor> },
      { path: 'users', element: <OnlyFor role="ADMIN"><UsersPage /></OnlyFor> },
      { path: 'users/:id', element: <OnlyFor role="ADMIN"><UserPage /></OnlyFor> },
      { path: 'record-readings', element: <OnlyFor role="ADMIN"><ReadingsPage /></OnlyFor> },
      { path: 'record-refusals', element: <OnlyFor role="ADMIN"><RefusalsPage /></OnlyFor> },
      { path: 'cancellation-log', element: <OnlyFor role="ADMIN"><CancellationLogPage /></OnlyFor> },
      { path: 'cash-desk-log', element: <OnlyFor role="ADMIN"><CashDeskLogPage /></OnlyFor> },
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
