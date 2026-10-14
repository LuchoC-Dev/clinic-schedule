import { pesos } from '../../shared/money';
import { MEANS } from '../words';

/** A day of the cash desk as reception reads it (D-87, D-88, D-89, D-90, D-103). */
export interface CashDay {
  date: string;
  movements: Movement[];
  total: Total;
  byRecorder: (Total & { recordedBy: string })[];
  pending: { visitId: string; patient: CashPatient; arrivedAt: string }[];
  covered: Covered[];
}

export interface CashPatient {
  id: string;
  fullName: string | null;
  document: string | null;
}

export interface Movement {
  kind: 'CHARGE' | 'REFUND';
  visitId: string;
  patient: CashPatient;
  emergency: boolean;
  copay: boolean;
  amount: number;
  means: string;
  left: number | null;
  reason: string | null;
  recordedBy: string;
  recordedAt: string;
  /** Its visit's current charge: what is left of it may be given back. */
  refundable: boolean;
  /** The last charge of a visit given back whole: the visit may be charged again. */
  chargeableAgain: boolean;
}

export interface Sum {
  count: number;
  amount: number;
}

export interface Total {
  charged: Sum;
  refunded: Sum;
  left: number;
  byMeans: { means: string; charged: Sum; refunded: Sum; left: number }[];
  drawer: number;
}

export interface Covered {
  visitId: string;
  patient: CashPatient;
  emergency: boolean;
  insurer: string;
  memberNumber: string | null;
  plan: string | null;
  token: string | null;
  recordedBy: string;
  recordedAt: string;
  withdrawal: { reason: string; recordedBy: string; recordedAt: string } | null;
  /** Its coverage withdrawn and nothing charged of it: charged privately. */
  chargeable: boolean;
}

/** `Ana Pérez — DNI 30111222`, as the cash desk names a patient. */
export function cashPatient(patient: CashPatient): string {
  if (!patient.fullName) return '(paciente desconocido)';
  return patient.document ? `${patient.fullName} — ${patient.document}` : patient.fullName;
}

/** A charge, the copay of a covered visit, or a refund. */
export function movementKind(movement: Movement): string {
  if (movement.kind === 'REFUND') return 'Devolución';
  return movement.copay ? 'Coseguro' : 'Cobro';
}

/** What went out of the drawer, with a minus. */
export function signedAmount(movement: Movement): string {
  return movement.kind === 'REFUND' ? `−${pesos(movement.amount)}` : pesos(movement.amount);
}

/** `OSDE · plan 310 · afiliado 61234567801`. */
export function coveredInsurance(covered: Covered): string {
  return covered.insurer + (covered.plan ? ` · plan ${covered.plan}` : '')
    + (covered.memberNumber ? ` · afiliado ${covered.memberNumber}` : '');
}

/** The tiles above the day, as the mock has them: what is in the drawer, each other means, and what went back. */
export function tiles(total: Total): { label: string; amount: string }[] {
  const others = total.byMeans.filter((each) => each.means !== 'CASH' && each.left !== 0);
  return [
    { label: 'Efectivo en caja', amount: pesos(total.drawer) },
    ...others.map((each) => ({ label: MEANS[each.means] ?? each.means, amount: pesos(each.left) })),
    { label: 'Devoluciones', amount: total.refunded.amount ? `−${pesos(total.refunded.amount)}` : pesos(0) },
  ];
}
