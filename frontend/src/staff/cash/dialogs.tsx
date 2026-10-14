import { Button, Group, Modal, Stack, Text, TextInput } from '@mantine/core';
import { type FormEvent, useState } from 'react';

import { pesos } from '../../shared/money';
import { AmountField, LargeAmountWarning, useConfirmedAmount } from '../components/AmountField';
import { MeansSelect } from '../components/fields';
import { Said } from '../components/Said';
import { useAction } from '../components/useAction';
import { api } from '../session';
import { CASH_REFUSALS } from '../words';

const DAY = ['cash-desk'];

/**
 * Giving back some or all of what is left of a charge, by its means, saying why (D-88); a very large amount is
 * confirmed a second time (D-101).
 */
export function RefundDialog({ visitId, about, left, onClose, onDone }: {
  visitId: string;
  about: string;
  left: number;
  onClose: () => void;
  onDone: () => void;
}) {
  const [amount, setAmount] = useState(String(left).replace('.', ','));
  const [reason, setReason] = useState('');
  const confirmed = useConfirmedAmount(amount);
  const refund = useAction(() => api.post(`/api/visits/${visitId}/refund`, { amount: confirmed.amount, reason: reason.trim() }),
    { refusals: CASH_REFUSALS, invalidates: DAY, onDone: () => { onDone(); onClose(); } });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    confirmed.submit(() => refund.run(undefined));
  };
  return (
    <Modal opened onClose={onClose} title="Devolver" centered>
      <form onSubmit={submit}>
        <Stack>
          <Text c="dimmed" fz={14}>{about} · queda {pesos(left)}</Text>
          <AmountField label="Importe a devolver" value={amount} onChange={setAmount} required />
          <TextInput label="Motivo" required maxLength={200} value={reason}
            onChange={(event) => setReason(event.currentTarget.value)} />
          <Said error={refund.error} />
          {confirmed.confirming && confirmed.amount !== null
            ? <LargeAmountWarning amount={confirmed.amount} verb="devolver" pending={refund.pending}
                onCancel={confirmed.cancel} onConfirm={() => confirmed.submit(() => refund.run(undefined))} />
            : (
              <Group justify="flex-end">
                <Button variant="default" onClick={onClose}>Cancelar</Button>
                <Button type="submit" color="red" loading={refund.pending}>Devolver</Button>
              </Group>
            )}
        </Stack>
      </form>
    </Modal>
  );
}

/**
 * An emergency's pending visit recorded as covered by the patient's insurance (D-90): its token, if the insurer asks
 * for one, and its copay, if any, a charge as any other — confirmed a second time when very large.
 */
export function CoverDialog({ visitId, about, onClose, onDone }: {
  visitId: string;
  about: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const [token, setToken] = useState('');
  const [copay, setCopay] = useState('');
  const [means, setMeans] = useState('CASH');
  const confirmed = useConfirmedAmount(copay);
  const cover = useAction(() => api.post(`/api/visits/${visitId}/coverage`, {
    token: token.trim() || null, copay: confirmed.amount ?? null, means,
  }), { refusals: CASH_REFUSALS, invalidates: DAY, onDone: () => { onDone(); onClose(); } });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    confirmed.submit(() => cover.run(undefined));
  };
  return (
    <Modal opened onClose={onClose} title="Con cobertura" centered>
      <form onSubmit={submit}>
        <Stack>
          <Text c="dimmed" fz={14}>{about}</Text>
          <TextInput label="Token (si lo pide)" maxLength={50} value={token} onChange={(event) => setToken(event.currentTarget.value)} />
          <AmountField label="Coseguro (si hay)" value={copay} onChange={setCopay} />
          <MeansSelect value={means} onChange={setMeans} />
          <Said error={cover.error} />
          {confirmed.confirming && confirmed.amount !== null
            ? <LargeAmountWarning amount={confirmed.amount} verb="cobrar" pending={cover.pending}
                onCancel={confirmed.cancel} onConfirm={() => confirmed.submit(() => cover.run(undefined))} />
            : (
              <Group justify="flex-end">
                <Button variant="default" onClick={onClose}>Cancelar</Button>
                <Button type="submit" loading={cover.pending}>Registrar con cobertura</Button>
              </Group>
            )}
        </Stack>
      </form>
    </Modal>
  );
}

/** A coverage marked by mistake withdrawn, saying why (D-90); the visit is then charged privately. */
export function WithdrawDialog({ visitId, about, onClose, onDone }: {
  visitId: string;
  about: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const [reason, setReason] = useState('');
  const withdraw = useAction(() => api.post(`/api/visits/${visitId}/coverage/withdrawal`, { reason: reason.trim() }),
    { refusals: CASH_REFUSALS, invalidates: DAY, onDone: () => { onDone(); onClose(); } });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    withdraw.run(undefined);
  };
  return (
    <Modal opened onClose={onClose} title="Quitar cobertura" centered>
      <form onSubmit={submit}>
        <Stack>
          <Text c="dimmed" fz={14}>{about}</Text>
          <TextInput label="Motivo" required maxLength={200} value={reason}
            onChange={(event) => setReason(event.currentTarget.value)} />
          <Said error={withdraw.error} />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>Cancelar</Button>
            <Button type="submit" color="red" loading={withdraw.pending}>Quitar cobertura</Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
