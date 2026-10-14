import { Alert, Button, TextInput } from '@mantine/core';
import { useState } from 'react';

import { needsConfirming, parseAmount, pesos } from '../../shared/money';

/** An amount in pesos, typed as the practice types it. */
export function AmountField({ value, onChange, label = 'Importe', required = false, placeholder }: {
  value: string;
  onChange: (value: string) => void;
  label?: string;
  required?: boolean;
  placeholder?: string;
}) {
  const typed = value.trim() !== '';
  const invalid = typed && parseAmount(value) === null;
  return (
    <TextInput label={label} value={value} required={required} inputMode="decimal" placeholder={placeholder ?? '0,00'}
      onChange={(event) => onChange(event.currentTarget.value)} classNames={{ input: 'mono' }}
      error={invalid ? 'Escribí un importe: 15000 o 15.000,50' : undefined} />
  );
}

/**
 * The second confirmation of a very large amount (D-87, D-100): sending it asks first, saying the amount, and only
 * "Sí, …" sends it; changing the amount asks again. The server refuses only what goes above its ceiling, so an
 * amount with one zero too many that stays below it would otherwise go through.
 */
export function useConfirmedAmount(amountText: string) {
  const [asking, setAsking] = useState<string | null>(null);
  const amount = parseAmount(amountText);
  const confirming = asking !== null && asking === amountText;
  return {
    amount,
    confirming,
    /** Sends it, or asks first if it is very large and not confirmed yet. */
    submit: (send: () => void) => {
      if (needsConfirming(amount) && !confirming) {
        setAsking(amountText);
        return;
      }
      setAsking(null);
      send();
    },
    cancel: () => setAsking(null),
  };
}

/** What a very large amount asks, with the button that sends it. */
export function LargeAmountWarning({ amount, verb, onConfirm, onCancel, pending }: {
  amount: number;
  /** What the button does: "cobrar", "devolver". */
  verb: string;
  onConfirm: () => void;
  onCancel: () => void;
  pending?: boolean;
}) {
  return (
    <Alert color="amber" role="alert" title="¿Seguro? Es un importe muy alto.">
      Vas a {verb} <strong className="mono">{pesos(amount)}</strong>. Revisá que no sobre un cero.
      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        <Button variant="default" onClick={onCancel}>Cancelar</Button>
        <Button color="amber" onClick={onConfirm} loading={pending}>Sí, {verb} {pesos(amount)}</Button>
      </div>
    </Alert>
  );
}
