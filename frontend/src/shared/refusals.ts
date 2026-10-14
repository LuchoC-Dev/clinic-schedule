import type { ApiError } from './api';
import type { Refusals } from './errors';

/**
 * A screen's refusals in its own words, by the refusal's code (D-100): the name the API gives each, which never
 * changes when its English message does. A code the screen does not know falls back to the sentence by status.
 */
export function byCode(words: Record<string, string>): Refusals {
  return (error: ApiError) => {
    const code = error.body?.code;
    return code ? words[code] : undefined;
  };
}

/** Several screens' words at once: the first that knows the code says it. */
export function combine(...all: Refusals[]): Refusals {
  return (error) => {
    for (const refusals of all) {
      const said = refusals(error);
      if (said) {
        return said;
      }
    }
    return undefined;
  };
}
