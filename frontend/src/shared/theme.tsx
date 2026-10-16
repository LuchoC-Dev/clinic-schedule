import '@fontsource/instrument-sans/400.css';
import '@fontsource/instrument-sans/500.css';
import '@fontsource/instrument-sans/600.css';
import '@fontsource/instrument-sans/700.css';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';
import '@mantine/core/styles.css';
import './theme.css';

import {
  type CSSVariablesResolver,
  type MantineColorSchemeManager,
  MantineProvider,
  createTheme,
} from '@mantine/core';
import type { ReactNode } from 'react';

/**
 * The look both applications share (D-97), from the approved mock: a calm petrol green for what is primary,
 * amber for warnings, white surfaces on a warm grey, Instrument Sans for the text and IBM Plex Mono for times and
 * amounts. The fonts are served by the application itself, never asked of another site.
 */
const theme = createTheme({
  primaryColor: 'petrol',
  primaryShade: 6,
  colors: {
    petrol: [
      '#EEF6F5', '#DCEBE8', '#B7D6D1', '#8FBFB8', '#5E9F97',
      '#2F7F78', '#0F5F5B', '#0A4441', '#07332F', '#04211F',
    ],
    amber: [
      '#FFF6EB', '#FCE9D0', '#F0D3AE', '#E5B47A', '#D18F42',
      '#B86A12', '#9A4A00', '#8A4B00', '#6E3C00', '#4A2E0B',
    ],
  },
  black: '#1B2321',
  fontFamily: "'Instrument Sans', system-ui, sans-serif",
  fontFamilyMonospace: "'IBM Plex Mono', ui-monospace, monospace",
  headings: { fontFamily: "'Instrument Sans', system-ui, sans-serif", fontWeight: '700' },
  defaultRadius: 'md',
  radius: { md: '8px', lg: '12px' },
});

const resolver: CSSVariablesResolver = () => ({
  variables: {},
  light: {
    '--mantine-color-body': '#F4F5F2',
    '--mantine-color-text': '#1B2321',
    '--mantine-color-dimmed': '#5C6662',
    '--mantine-color-default-border': '#DADDD6',
  },
  dark: {},
});

/**
 * The colour scheme is always the light one, and kept nowhere: Mantine would otherwise keep it in the browser's
 * storage, where these applications keep nothing (D-97).
 */
const noStorage: MantineColorSchemeManager = {
  get: (defaultValue) => defaultValue,
  set: () => {},
  subscribe: () => {},
  unsubscribe: () => {},
  clear: () => {},
};

/**
 * Under Vitest, Mantine's test environment: no transitions, no portals, and no hiding a dropdown by where it is
 * placed. A menu or a dialog otherwise appears only after two animation frames and a timer, and jsdom, which lays
 * nothing out, may take its trigger for hidden; under a full build's load both come late, and a test looking for
 * what opened gave up first (D-113). Vite replaces the mode when it builds, so the applications never take it.
 */
const ENV = import.meta.env.MODE === 'test' ? 'test' : 'default';

export function Theme({ children }: { children: ReactNode }) {
  return (
    <MantineProvider theme={theme} cssVariablesResolver={resolver} colorSchemeManager={noStorage}
      forceColorScheme="light" env={ENV}>
      {children}
    </MantineProvider>
  );
}
