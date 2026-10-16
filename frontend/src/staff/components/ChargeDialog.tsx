import { Button, Group, Modal, Stack, Text } from '@mantine/core';
import { type FormEvent, useState } from 'react';

import type { Refusals } from '../../shared/errors';
import { AmountField, LargeAmountWarning, useConfirmedAmount } from './AmountField';
import { type Coverage, CoverageFields, coverageOf, MeansSelect } from './fields';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';

/** What a visit is charged as it comes in (D-87, D-89, D-90): an amount, its means, and its coverage, if any. */
export interface Charge {
  amount: number | undefined;
  means: string;
  coverage: { token: string | null } | undefined;
}

/**
 * Charging a visit as the patient comes in (D-87): the amount and its means, or the patient's insurance with its
 * copay, if any (D-90). An emergency may come in with nothing charged, left pending. A very large amount is
 * confirmed a second time (D-101).
 */
export function ChargeDialog({ title, about, button, emergency = false, withCoverage = true, refusals, invalidates,
  send, onClose, onDone }: {
  title: string;
  about: string;
  button: string;
  emergency?: boolean;
  withCoverage?: boolean;
  refusals: Refusals;
  invalidates: string[];
  send: (charge: Charge) => Promise<unknown>;
  onClose: () => void;
  /** Once it went through, before the dialog closes: for the screen to say so. */
  onDone?: () => void;
}) {
  const [amount, setAmount] = useState('');
  const [means, setMeans] = useState('CASH');
  const [coverage, setCoverage] = useState<Coverage>({ covered: false, token: '' });
  const confirmed = useConfirmedAmount(amount);
  const charge = useAction(() => send({
    amount: confirmed.amount ?? undefined, means, coverage: coverageOf(coverage),
  }), { refusals, invalidates, onDone: () => { onDone?.(); onClose(); } });

  function submit(event: FormEvent) {
    event.preventDefault();
    confirmed.submit(() => charge.run(undefined));
  }

  return (
    <Modal opened onClose={onClose} title={title} centered>
      <form onSubmit={submit}>
        <Stack>
          <Text c="dimmed" fz={14}>{about}</Text>
          <AmountField value={amount} onChange={setAmount} required={!emergency && !coverage.covered}
            label={coverage.covered ? 'Coseguro (si hay)' : emergency ? 'Importe (una urgencia puede quedar pendiente)' : 'Importe'} />
          <MeansSelect value={means} onChange={setMeans} />
          {withCoverage && <CoverageFields value={coverage} onChange={setCoverage} />}
          <Said error={charge.error} />
          {confirmed.confirming && confirmed.amount !== null
            ? <LargeAmountWarning amount={confirmed.amount} verb="cobrar" pending={charge.pending}
                onCancel={confirmed.cancel} onConfirm={() => confirmed.submit(() => charge.run(undefined))} />
            : (
              <Group justify="flex-end">
                <Button variant="default" onClick={onClose}>Cancelar</Button>
                <Button type="submit" loading={charge.pending}>{button}</Button>
              </Group>
            )}
        </Stack>
      </form>
    </Modal>
  );
}
