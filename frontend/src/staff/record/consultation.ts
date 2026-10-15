import type { ApiError } from '../../shared/api';
import { combine, byCode } from '../../shared/refusals';
import type { Refusals } from '../../shared/errors';

/**
 * A consultation being written (D-47, D-51, D-55): kept in the screen's memory alone until it is sent, once, whole —
 * nothing of it goes to the browser's storage (D-97). Its rows are added as they are needed; an empty row is dropped,
 * and a row half filled is refused rather than guessed, as the Thymeleaf form does.
 */
export interface Draft {
  /** Sent with it, so a retry after a lost answer records it once (D-19). */
  id: string;
  notes: string;
  prescriptions: string[];
  checks: CheckRow[];
  orders: string[];
}

export interface CheckRow {
  description: string;
  result: string;
  /** The patient's open order it fulfils, or empty (D-55). */
  orderId: string;
}

export function newDraft(id: string): Draft {
  return { id, notes: '', prescriptions: [], checks: [], orders: [] };
}

export const EMPTY_CHECK: CheckRow = { description: '', result: '', orderId: '' };

/** Whether anything was typed, so leaving it is asked first. */
export function isTouched(draft: Draft): boolean {
  return draft.notes.trim() !== '' || draft.prescriptions.some(filled) || draft.orders.some(filled)
    || draft.checks.some((row) => !isEmptyCheck(row));
}

export interface ConsultationRequest {
  id: string;
  patientId: string;
  notes: string;
  items: { kind: 'PRESCRIPTION'; description: string }[];
  checks: { description: string | null; result: string | null; orderId: string | null }[];
  orders: { description: string }[];
}

/** What would be sent, or what the screen can tell is wrong before sending it: the rules proper are the server's. */
export function toRequest(draft: Draft, patientId: string): { request: ConsultationRequest } | { problem: string } {
  if (!filled(draft.notes)) {
    return { problem: 'Escribí lo consultado.' };
  }
  const checks = draft.checks.filter((row) => !isEmptyCheck(row));
  if (checks.some((row) => !filled(row.description) && !row.orderId)) {
    return { problem: 'Cada estudio necesita qué se hizo o la orden que cumple: completá o vaciá los renglones a medias.' };
  }
  return {
    request: {
      id: draft.id,
      patientId,
      notes: draft.notes,
      items: draft.prescriptions.filter(filled).map((description) => ({ kind: 'PRESCRIPTION', description })),
      checks: checks.map((row) => ({
        description: filled(row.description) ? row.description : null,
        result: filled(row.result) ? row.result : null,
        orderId: row.orderId || null,
      })),
      orders: draft.orders.filter(filled).map((description) => ({ description })),
    },
  };
}

/** A check done without a consultation (D-53): what was done, or the order it fulfils; its result, or pending. */
export interface CheckRequest {
  id: string;
  patientId: string;
  description: string | null;
  result: string | null;
  orderId: string | null;
}

export function checkRequest(row: CheckRow, patientId: string, id: string): { request: CheckRequest } | { problem: string } {
  if (!filled(row.description) && !row.orderId) {
    return { problem: 'Escribí qué estudio se hizo, o elegí la orden que cumple.' };
  }
  return {
    request: {
      id,
      patientId,
      description: filled(row.description) ? row.description : null,
      result: filled(row.result) ? row.result : null,
      orderId: row.orderId || null,
    },
  };
}

function filled(text: string): boolean {
  return text.trim() !== '';
}

function isEmptyCheck(row: CheckRow): boolean {
  return !filled(row.description) && !filled(row.result) && !row.orderId;
}

/** A refusal with no code of its own: what Bean Validation refuses, by its status. */
function byStatus(status: number, words: string): Refusals {
  return (error: ApiError) => (error.status === status && !error.body?.code ? words : undefined);
}

const NOT_ARRIVED = 'La consulta se registra cuando el paciente llegó a atenderse con vos hoy.';
const NO_ACCESS = 'No podés leer esta historia clínica.';

/** Sending a consultation (D-47, D-55). */
export const CONSULTATION_REFUSALS = combine(byCode({
  NOT_ARRIVED,
  NO_ACCESS,
  UNKNOWN_ORDER: 'Una de las órdenes elegidas ya no está abierta: revisá los estudios.',
  ORDER_NOT_OPEN: 'Una de las órdenes elegidas ya no está abierta: revisá los estudios.',
  ILLEGAL_ARGUMENT: 'Revisá la consulta: lo consultado tiene hasta 10 000 caracteres, cada ítem y cada estudio hasta '
    + '2 000, cada resultado hasta 10 000, y son 50 de cada uno como máximo.',
}), byStatus(400, 'Revisá la consulta: lo consultado tiene hasta 10 000 caracteres, cada ítem y cada estudio hasta '
  + '2 000, cada resultado hasta 10 000, y son 50 de cada uno como máximo.'));

/** A check done without a consultation (D-53). */
export const CHECK_REFUSALS = combine(byCode({
  NOT_ARRIVED: 'El estudio se registra cuando el paciente llegó a atenderse con vos hoy.',
  NO_ACCESS,
  UNKNOWN_ORDER: 'Esa orden ya no está abierta.',
  ORDER_NOT_OPEN: 'Esa orden ya no está abierta.',
  ILLEGAL_ARGUMENT: 'Revisá el estudio: qué se hizo tiene hasta 2 000 caracteres, y el resultado hasta 10 000.',
}), byStatus(400, 'Revisá el estudio: qué se hizo tiene hasta 2 000 caracteres, y el resultado hasta 10 000.'));

/** An addendum, to a consultation or a check (D-52, D-58). */
export const ADDENDUM_REFUSALS = combine(byCode({
  NO_ACCESS,
  CONSULTATION_CHANGED: 'Otro profesional agregó una aclaración a esta consulta recién: revisala y volvé a enviar la tuya.',
  CHECK_CHANGED: 'Otro profesional cambió este estudio recién: revisalo y volvé a enviar tu aclaración.',
  ILLEGAL_ARGUMENT: 'La aclaración tiene hasta 2 000 caracteres.',
}), byStatus(400, 'La aclaración tiene hasta 2 000 caracteres.'));

/** A file attached (D-57, D-94). */
export const FILE_REFUSALS = combine(byCode({
  NO_ACCESS,
  UNSUPPORTED_FILE: 'Solo se adjuntan archivos PDF, PNG o JPG.',
  FILE_TOO_LARGE: 'El archivo supera los 10 MB.',
}), byStatus(413, 'El archivo supera los 10 MB.'));

/** A result recorded later, in words, in a file, or both (D-53, D-57). */
export const RESULT_REFUSALS = combine(FILE_REFUSALS, byCode({
  RESULT_ALREADY_RECORDED: 'Ese estudio ya tiene resultado: se cargó recién.',
  CHECK_CHANGED: 'Ese estudio ya tiene resultado: se cargó recién.',
  ILLEGAL_ARGUMENT: 'El resultado tiene hasta 10 000 caracteres.',
}), byStatus(400, 'El resultado tiene hasta 10 000 caracteres.'));

/** Cancelling an order, with a reason (D-55). */
export const CANCEL_ORDER_REFUSALS = combine(byCode({
  NOT_THE_ORDER_AUTHOR: 'Solo quien emitió la orden puede anularla.',
  ORDER_NOT_OPEN: 'Esa orden ya no está abierta: se cumplió o se anuló recién.',
  ILLEGAL_ARGUMENT: 'El motivo tiene hasta 500 caracteres.',
}), byStatus(400, 'El motivo tiene hasta 500 caracteres.'));

/** Reading the record. */
export const RECORD_REFUSALS = byCode({ NO_ACCESS });
