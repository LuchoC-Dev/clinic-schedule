import { Button, Group, Text } from '@mantine/core';
import { type ReactNode, useEffect, useState } from 'react';
import { NavLink } from 'react-router';

import { Brand } from '../shared/Brand';
import { nowAtThePractice } from '../shared/dates';
import './layout.css';
import { type MenuEntry, PASSWORD, ROLE_NAMES, menuFor } from './navigation';
import { logOut, useUser } from './session';

/**
 * The staff's screens, for a desktop (D-97): a white header with the practice, who is logged in, the practice's
 * day and time, and "Salir"; the menu of the role on the left; the screen on the right.
 */
export function Layout({ children }: { children: ReactNode }) {
  const user = useUser();
  const [leaving, setLeaving] = useState(false);
  return (
    <div className="staff">
      <header className="staff-header">
        <Group gap={12} wrap="nowrap">
          <Brand />
          <Text component="span" fz={14} c="dimmed" className="staff-who">
            {ROLE_NAMES[user.role]} · {user.username}
          </Text>
        </Group>
        <Group gap={16} fz={14}>
          <PracticeClock />
          <Button variant="default" loading={leaving}
            onClick={() => { setLeaving(true); void logOut(); }}>
            Salir
          </Button>
        </Group>
      </header>
      <div className="staff-body">
        <nav className="staff-nav" aria-label="Secciones">
          {menuFor(user).map((entry) => <MenuLink key={entry.to} entry={entry} />)}
          <div className="staff-nav-end"><MenuLink entry={PASSWORD} /></div>
        </nav>
        <main className="staff-main">{children}</main>
      </div>
    </div>
  );
}

function MenuLink({ entry }: { entry: MenuEntry }) {
  if (entry.inApp) {
    return <NavLink to={entry.to} className={({ isActive }) => (isActive ? 'on' : undefined)}>{entry.label}</NavLink>;
  }
  return (
    <a href={entry.to} target={entry.newTab ? '_blank' : undefined} rel={entry.newTab ? 'noopener' : undefined}>
      {entry.label}
    </a>
  );
}

/** The practice's day and time, kept current; only shown, never used for a rule. */
function PracticeClock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 15_000);
    return () => window.clearInterval(timer);
  }, []);
  return <Text component="span" fz={14}>{nowAtThePractice(now)}</Text>;
}
