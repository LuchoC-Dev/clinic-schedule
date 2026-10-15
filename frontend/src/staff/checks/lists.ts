import { addDays, when } from '../../shared/dates';
import { nameOf } from '../record/record';

/**
 * What the practitioner's lists of checks decide (D-54, D-56, D-105): the checks waiting for a result, and the orders
 * they issued — where each stands, never what a result says, which is in the record.
 */

/** A check waiting for its result, as `/api/checks/pending` answers it: the patient and the check, nothing else. */
export interface PendingCheck {
  checkId: string;
  patientId: string;
  patientName: string | null;
  patientDocument: string | null;
  description: string;
  performedAt: string;
  performedByName: string | null;
  mayRead: boolean;
}

/** An order the practitioner issued and did not cancel, as `/api/orders/mine` answers it (D-56). */
export interface MyOrder {
  orderId: string;
  patientId: string;
  patientName: string | null;
  patientDocument: string | null;
  description: string;
  orderedAt: string;
  situation: 'OPEN' | 'AWAITING_RESULT' | 'REPORTED';
  performedAt: string | null;
  performedByName: string | null;
  reportedAt: string | null;
  reportedByName: string | null;
  mayRead: boolean;
}

/** `Ana Pérez — DNI 30111222`, as the lists name a patient. */
export function patientOf(row: { patientName: string | null; patientDocument: string | null }): string {
  if (!row.patientName) {
    return '(paciente desconocido)';
  }
  return row.patientDocument ? `${row.patientName} — ${row.patientDocument}` : row.patientName;
}

/** Where an order stands, in the Thymeleaf list's words. */
export function orderSituation(order: MyOrder): string {
  switch (order.situation) {
    case 'OPEN':
      return 'Abierta: nadie hizo el estudio todavía';
    case 'AWAITING_RESULT':
      return `Realizada el ${when(order.performedAt ?? '')} por ${nameOf(order.performedByName)}; espera el resultado`;
    case 'REPORTED':
      return `Resultado del ${when(order.reportedAt ?? '')}, de ${nameOf(order.reportedByName)}`;
  }
}

/** How far back the reported orders go unless all are asked for (D-56). */
export const RECENT_DAYS = 30;

/**
 * The list split in two, as the screen shows it: what waits, the oldest first, since it is what is overdue; and what
 * was reported, the newest first, from the last 30 days unless all are asked for — nothing marks a result as seen, so
 * how recent it is stands for how new it is (D-56).
 */
export function splitOrders(orders: MyOrder[], today: string, all: boolean) {
  const since = `${addDays(today, -RECENT_DAYS)}T00:00:00`;
  return {
    waiting: orders.filter((order) => order.situation !== 'REPORTED')
      .sort((a, b) => a.orderedAt.localeCompare(b.orderedAt)),
    reported: orders.filter((order) => order.situation === 'REPORTED')
      .filter((order) => all || (order.reportedAt ?? '') >= since)
      .sort((a, b) => (b.reportedAt ?? '').localeCompare(a.reportedAt ?? '')),
  };
}
