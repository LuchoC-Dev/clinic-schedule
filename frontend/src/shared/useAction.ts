import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { describeError, type Refusals } from './errors';

/**
 * One thing someone does — book, cancel, charge —, sent once: while it goes, the button waits; what it says
 * afterwards is the screen's notice, or its refusal in Spanish (D-100); and what it changed is read again. A refusal
 * leaves the form as it was typed, so it is sent again as it is or changed (D-52).
 */
export function useAction<Input, Output>(send: (input: Input) => Promise<Output>, options: {
  refusals?: Refusals;
  /** What the screen says once it is done. */
  done?: string | ((output: Output) => string);
  /** The queries whose answers it changed, by the start of their keys. */
  invalidates?: string[];
  onDone?: (output: Output) => void;
}) {
  const queries = useQueryClient();
  const [notice, setNotice] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: send,
    onMutate: () => setNotice(null),
    onSuccess: async (output) => {
      await Promise.all((options.invalidates ?? []).map((key) => queries.invalidateQueries({ queryKey: [key] })));
      const done = options.done;
      setNotice(typeof done === 'function' ? done(output) : done ?? null);
      options.onDone?.(output);
    },
  });
  return {
    run: (input: Input) => mutation.mutate(input),
    pending: mutation.isPending,
    error: mutation.isError ? describeError(mutation.error, options.refusals) : null,
    /** The refusal itself, for a screen whose state it changes. */
    failure: mutation.isError ? mutation.error : null,
    notice,
    reset: () => {
      mutation.reset();
      setNotice(null);
    },
  };
}
