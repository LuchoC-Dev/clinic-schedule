import { type FormEvent, useState } from 'react';
import { useSearchParams } from 'react-router';

/**
 * A search kept in the address, so going back and reloading keep it: what is typed stays in the form until it is
 * sent, and only then becomes the address — and the query, which reads `query`. An empty field is left out.
 */
export function useAddressSearch<Key extends string>(keys: readonly Key[]) {
  const [params, setParams] = useSearchParams();
  const query = Object.fromEntries(keys.map((key) => [key, params.get(key)])) as Record<Key, string | null>;
  const [typed, setTyped] = useState(() => Object.fromEntries(keys.map((key) => [key, query[key] ?? ''])) as Record<Key, string>);

  function submit(event: FormEvent) {
    event.preventDefault();
    const search = new URLSearchParams();
    for (const key of keys) {
      const value = typed[key].trim();
      if (value) search.set(key, value);
    }
    setParams(search);
  }

  return {
    query,
    typed,
    type: (key: Key) => (event: { currentTarget: { value: string } }) => {
      const { value } = event.currentTarget;
      setTyped((before) => ({ ...before, [key]: value }));
    },
    submit,
  };
}
