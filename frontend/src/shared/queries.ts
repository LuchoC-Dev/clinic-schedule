import { QueryClient } from '@tanstack/react-query';

/**
 * What an application reads lives in memory only (D-97), and goes with the page: logging out reloads it. A
 * refusal is shown at once rather than asked again.
 */
export function newQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}
