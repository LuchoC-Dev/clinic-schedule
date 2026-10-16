import { Button } from '@mantine/core';
import { type ReactNode, useState } from 'react';
import { NavLink } from 'react-router';

import { Brand } from '../shared/Brand';
import './layout.css';
import { logOut } from './session';

/**
 * The patients' sections, in the bar at the bottom (D-97). A section not moved here yet is the Thymeleaf screen,
 * reached with a plain link: both share the patient's session (D-80).
 */
export const TABS: { label: string; to: string; inApp: boolean; icon: string }[] = [
  { label: 'Turnos', to: '/appointments', inApp: true, icon: 'M3 5h18v16H3zM3 10h18M8 3v4M16 3v4' },
  { label: 'Familia', to: '/portal/family', inApp: false, icon: 'M6 8a3 3 0 1 0 6 0a3 3 0 1 0-6 0M14.5 10a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0'
      + 'M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M15 20c0-2 1-3.5 2-3.5s4 .5 4 3.5' },
  { label: 'Historia', to: '/portal/record-copies', inApp: false, icon: 'M6 3h9l4 4v14H6zM14 3v5h5M9 13h7M9 17h5' },
  { label: 'Perfil', to: '/portal/profile', inApp: false, icon: 'M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8' },
];

/** The patients' screens, made for a phone first: a simple header, the screen, and the sections below. */
export function Layout({ children }: { children: ReactNode }) {
  const [leaving, setLeaving] = useState(false);
  return (
    <div className="patients">
      <header className="patients-header">
        <Brand size={17} />
        <Button variant="default" size="sm" loading={leaving} onClick={() => { setLeaving(true); void logOut(); }}>
          Salir
        </Button>
      </header>
      <main className="patients-main">{children}</main>
      <nav className="patients-tabs" aria-label="Secciones">
        {TABS.map((tab) => {
          const icon = (
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
              aria-hidden="true">
              <path d={tab.icon} />
            </svg>
          );
          return tab.inApp
            ? <NavLink key={tab.to} to={tab.to} className={({ isActive }) => (isActive ? 'on' : undefined)}>{icon}{tab.label}</NavLink>
            : <a key={tab.to} href={tab.to}>{icon}{tab.label}</a>;
        })}
      </nav>
    </div>
  );
}
