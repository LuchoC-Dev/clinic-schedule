import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * Besides the usual: nothing is kept in the browser's storage (D-97). What the applications read — a clinical
 * record above all (D-51) — lives in memory and goes with the page.
 */
const NOT_KEPT = 'The applications keep nothing in the browser (D-97): keep it in memory.';

export default tseslint.config(
  { ignores: ['node', 'node_modules', '../target'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: { globals: globals.browser },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'no-restricted-globals': ['error',
        { name: 'localStorage', message: NOT_KEPT },
        { name: 'sessionStorage', message: NOT_KEPT },
        { name: 'indexedDB', message: NOT_KEPT },
        { name: 'caches', message: NOT_KEPT }],
      'no-restricted-properties': ['error',
        { object: 'window', property: 'localStorage', message: NOT_KEPT },
        { object: 'window', property: 'sessionStorage', message: NOT_KEPT },
        { object: 'window', property: 'indexedDB', message: NOT_KEPT },
        { object: 'window', property: 'caches', message: NOT_KEPT }],
    },
  },
);
