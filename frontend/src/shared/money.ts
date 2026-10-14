/**
 * Pesos as the practice reads and types them (D-87): `$ 15.000,00`. A charge is above zero, with two decimals at
 * most; the server refuses what goes above its ceiling. An amount with one zero too many stays below it, so the
 * screens ask to confirm a very large one a second time before sending it (D-100).
 */

/** Above this, an amount is confirmed a second time: the approved mock's. */
export const CONFIRM_ABOVE = 500_000;

const PESOS = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** `$ 15.000,00`. */
export function pesos(amount: number): string {
  return `$ ${PESOS.format(amount)}`;
}

/**
 * The amount typed, or null when it is not one: `15000`, `15.000`, `15.000,50` and `15000,5` are the practice's
 * ways; `15000.50` is taken as a point for the decimals, as a calculator would.
 */
export function parseAmount(typed: string): number | null {
  const text = typed.trim().replace(/^\$\s*/, '');
  let normal: string;
  if (text.includes(',')) {
    normal = text.replace(/\./g, '').replace(',', '.');
  } else if (/^\d{1,3}(\.\d{3})+$/.test(text)) {
    normal = text.replace(/\./g, '');
  } else {
    normal = text;
  }
  if (!/^\d+(\.\d{1,2})?$/.test(normal)) {
    return null;
  }
  const amount = Number(normal);
  return amount > 0 ? amount : null;
}

/** Whether an amount is so large it is confirmed a second time. */
export function needsConfirming(amount: number | null): boolean {
  return amount !== null && amount > CONFIRM_ABOVE;
}
