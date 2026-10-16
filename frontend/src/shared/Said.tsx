import { Alert } from '@mantine/core';

/** What the last action said: its refusal, in red and announced, or that it is done. */
export function Said({ error, notice }: { error?: string | null; notice?: string | null }) {
  if (error) {
    return <Alert color="red" role="alert">{error}</Alert>;
  }
  if (notice) {
    return <Alert color="petrol" role="status">{notice}</Alert>;
  }
  return null;
}
