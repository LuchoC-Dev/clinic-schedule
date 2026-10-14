import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

/**
 * The tests run in a time zone far from the practice's, so a day or a time that followed the browser's zone
 * instead of the practice's would show (D-10).
 */
process.env.TZ = 'Pacific/Kiritimati';

/** Unit and component tests on what the screens decide (D-11), in a simulated browser, run by `mvnw test`. */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['src/test/setup.ts'],
    css: false,
  },
});
