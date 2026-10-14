import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});

// Mantine reads these, which jsdom does not have.
window.matchMedia = window.matchMedia ?? ((query: string) => ({
  matches: false, media: query, onchange: null,
  addListener: () => {}, removeListener: () => {}, addEventListener: () => {}, removeEventListener: () => {},
  dispatchEvent: () => false,
}));
window.ResizeObserver = window.ResizeObserver ?? class {
  observe() {}
  unobserve() {}
  disconnect() {}
};
