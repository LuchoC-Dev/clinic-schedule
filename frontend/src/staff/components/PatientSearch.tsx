import { Button, Group, TextInput } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';

import { api } from '../session';
import type { Patient } from '../types';

/** Patients found by name, document or alias (D-60); none until something is searched. */
export function usePatientSearch(query: string) {
  return useQuery({
    queryKey: ['patients', query],
    queryFn: () => api.get<Patient[]>('/api/patients', { query }),
    enabled: query.trim() !== '',
  });
}

/** A search box that asks when it is sent, not at every key. */
export function SearchBox({ initial = '', onSearch, label = 'Buscar paciente' }: {
  initial?: string;
  onSearch: (query: string) => void;
  label?: string;
}) {
  const [typed, setTyped] = useState(initial);
  function submit(event: FormEvent) {
    event.preventDefault();
    onSearch(typed.trim());
  }
  return (
    <form onSubmit={submit}>
      <Group align="flex-end">
        <TextInput label={label} placeholder="nombre, documento o NN" value={typed}
          onChange={(event) => setTyped(event.currentTarget.value)} />
        <Button type="submit" variant="default">Buscar</Button>
      </Group>
    </form>
  );
}
