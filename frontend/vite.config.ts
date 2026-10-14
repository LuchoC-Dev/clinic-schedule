import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';
import { defineConfig } from 'vite';

/**
 * The two applications, each built on its own by `vite build --mode staff` or `--mode patients` (D-97): from its
 * folder, for the address Spring serves it at, into `target/classes/static`, so Maven packs it in the jar.
 * `/app/` and `/portal/app/` while the Thymeleaf screens keep their paths; `/` and `/portal/` once they are gone.
 *
 * In development (`npm run dev:staff`, `npm run dev:patients`) everything outside the application — the API and
 * the Thymeleaf screens — goes to the application running on port 8080, so the session is one.
 */
const APPLICATIONS = {
  staff: { root: 'src/staff', base: '/app/', outDir: '../target/classes/static/app', port: 5173 },
  patients: { root: 'src/patients', base: '/portal/app/', outDir: '../target/classes/static/portal/app', port: 5174 },
} as const;

export default defineConfig(({ mode }) => {
  const application = APPLICATIONS[mode as keyof typeof APPLICATIONS];
  if (!application) {
    throw new Error(`Build one application at a time: --mode staff or --mode patients, not "${mode}"`);
  }
  const here = import.meta.dirname;
  return {
    plugins: [react()],
    root: resolve(here, application.root),
    base: application.base,
    build: { outDir: resolve(here, application.outDir), emptyOutDir: true, target: 'es2022' },
    server: {
      port: application.port,
      strictPort: true,
      proxy: { [`^(?!${application.base.replace(/\/$/, '')}(/|$)).*`]: 'http://localhost:8080' },
    },
  };
});
