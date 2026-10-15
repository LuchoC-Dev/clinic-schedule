import { describe, expect, it } from 'vitest';

import { ApiError } from '../../shared/api';
import { describeError } from '../../shared/errors';
import { check, clinicalRecord, consultation, file, order } from '../../test/record';
import { CONSULTATION_REFUSALS, FILE_REFUSALS, RESULT_REFUSALS, checkRequest, isTouched, newDraft, toRequest } from './consultation';
import {
  ageOn, allChecks, filesOf, grantedFor, historyOf, mayCancel, openOrders, orderState, patientLine, resultOf, sizeOf,
} from './record';

describe('the patient beside the record', () => {
  /** In years, and in months below two, as paediatrics counts it — in days for a baby not a month old (D-73). */
  it('says their age as a practitioner reads it', () => {
    expect(ageOn('1980-03-11', '2026-03-10')).toBe('45 años');
    expect(ageOn('1980-03-10', '2026-03-10')).toBe('46 años');
    expect(ageOn('2024-03-11', '2026-03-10')).toBe('23 meses');
    expect(ageOn('2026-02-10', '2026-03-10')).toBe('1 mes');
    expect(ageOn('2026-02-11', '2026-03-10')).toBe('27 días');
    expect(ageOn('2026-03-09', '2026-03-10')).toBe('1 día');
    expect(ageOn(null, '2026-03-10')).toBeNull();
  });

  it('says who they are in one line: document, age, insurance and whether reception saw the document', () => {
    const { patient } = clinicalRecord();
    expect(patientLine(patient, '2026-03-10')).toBe('DNI 30123456 · 46 años · OSDE · plan 310 · identidad verificada');
    expect(patientLine({ ...patient, document: null, birthDate: null, insurance: null, identityVerified: false },
      '2026-03-10')).toBe('sin obra social cargada · identidad no verificada');
    expect(patientLine({ ...patient, insurance: { insurer: 'IOMA', memberNumber: '123', plan: null } }, '2026-03-10'))
      .toContain('IOMA · afiliado 123');
  });
});

describe("the record's history", () => {
  it('mixes consultations, checks without one and files of the record by time, the newest first', () => {
    const record = clinicalRecord({
      consultations: [consultation({ id: 'c1', recordedAt: '2026-03-02T09:00:00' })],
      checks: [check({ id: 'k1', performedAt: '2026-03-05T10:00:00' })],
      attachments: [file({ id: 'f1', uploadedAt: '2026-01-10T12:00:00' }),
        file({ id: 'f2', owner: 'CHECK', checkId: 'k1', uploadedAt: '2026-03-06T08:00:00' })],
    });
    expect(historyOf(record).map((entry) => entry.kind)).toEqual(['check', 'consultation', 'file']);
  });

  it('gathers every check, from the consultations too, the newest first', () => {
    const record = clinicalRecord({
      consultations: [consultation({ checks: [check({ id: 'k2', consultationId: 'c1', performedAt: '2026-03-02T09:10:00' })] })],
      checks: [check({ id: 'k1', performedAt: '2026-03-05T10:00:00' })],
    });
    expect(allChecks(record).map((each) => each.id)).toEqual(['k1', 'k2']);
  });

  it('offers only the open orders to fulfil, the oldest first', () => {
    const record = clinicalRecord({ consultations: [consultation({ orders: [
      order({ id: 'o1', orderedAt: '2026-03-01T09:00:00' }),
      order({ id: 'o2', orderedAt: '2026-03-03T09:00:00' }),
      order({ id: 'o3', status: 'CANCELLED', cancellationReason: 'no hace falta' }),
    ] })] });
    expect(openOrders(record).map((each) => each.id)).toEqual(['o1', 'o2']);
  });

  it("finds the files of each owner, in the order they were uploaded", () => {
    const record = clinicalRecord({ attachments: [
      file({ id: 'f2', owner: 'CONSULTATION', consultationId: 'c1', uploadedAt: '2026-03-03T09:00:00' }),
      file({ id: 'f1', owner: 'CONSULTATION', consultationId: 'c1', uploadedAt: '2026-03-02T09:00:00' }),
      file({ id: 'f3', owner: 'CONSULTATION', consultationId: 'c2' }),
      file({ id: 'f4', owner: 'CHECK', checkId: 'k1' }),
      file({ id: 'f5' }),
    ] });
    expect(filesOf(record, 'CONSULTATION', 'c1').map((each) => each.id)).toEqual(['f1', 'f2']);
    expect(filesOf(record, 'CHECK', 'k1').map((each) => each.id)).toEqual(['f4']);
    expect(filesOf(record, 'RECORD').map((each) => each.id)).toEqual(['f5']);
  });
});

describe('where each thing stands', () => {
  it('says a check is pending, or its result in words or in its file, with who and when', () => {
    expect(resultOf(check())).toEqual({ pending: true, text: 'Resultado pendiente.', by: null });
    const reported = check({ pending: false, result: { authorId: 'd2', authorName: 'Dr. Benítez',
      writtenAt: '2026-03-11T12:00:00', text: 'Hemoglobina 13,5 g/dl' } });
    expect(resultOf(reported)).toEqual({ pending: false, text: 'Hemoglobina 13,5 g/dl', by: 'Dr. Benítez, mié 11/03/2026 12:00' });
    expect(resultOf({ ...reported, result: { ...reported.result!, text: null } }).text).toBe('en el archivo adjunto');
  });

  it('says an order is open, fulfilled when its check was done, or cancelled with its reason', () => {
    const done = check({ id: 'k9', performedAt: '2026-03-12T08:30:00' });
    expect(orderState(order(), [])).toBe('Abierta');
    expect(orderState(order({ status: 'FULFILLED', fulfilledBy: 'k9' }), [done])).toBe('Cumplida el jue 12/03/2026 08:30');
    expect(orderState(order({ status: 'FULFILLED', fulfilledBy: 'k8' }), [done])).toBe('Cumplida');
    expect(orderState(order({ status: 'CANCELLED', cancellationReason: 'Se hizo en otro lado' }), []))
      .toBe('Anulada: Se hizo en otro lado');
  });

  /** Only its author, while it is open (D-55). */
  it('lets only its author cancel an order, while it is open', () => {
    expect(mayCancel(order(), 'd1')).toBe(true);
    expect(mayCancel(order(), 'd2')).toBe(false);
    expect(mayCancel(order({ status: 'FULFILLED' }), 'd1')).toBe(false);
    expect(mayCancel(order(), null)).toBe(false);
  });

  it("says a file's size and what an access was granted for", () => {
    expect(sizeOf(500)).toBe('1 KB');
    expect(sizeOf(120 * 1024)).toBe('120 KB');
    expect(sizeOf(1.5 * 1024 * 1024)).toBe('1,5 MB');
    const grant = { id: 'g', practitionerId: 'd2', practitionerName: null, checkId: 'k1', recordCopyId: null,
      grantedAt: '2026-03-10T10:00:00', validUntil: '2027-03-10', reason: null };
    expect(grantedFor(grant, [check()])).toBe('Extracción de sangre');
    expect(grantedFor({ ...grant, checkId: null, recordCopyId: 'r1' }, [])).toBe('Autenticar una copia de la historia');
  });
});

describe('a consultation being written', () => {
  const draft = { ...newDraft('id-1'), notes: 'Control.' };

  it('needs what was consulted', () => {
    expect(toRequest({ ...draft, notes: '  ' }, 'p1')).toEqual({ problem: 'Escribí lo consultado.' });
  });

  it('drops the empty rows and sends the rest, with its id', () => {
    const built = toRequest({
      ...draft,
      prescriptions: ['enalapril 10 mg', ' '],
      checks: [{ description: 'ECG', result: '', orderId: '' }, { description: '', result: '', orderId: '' },
        { description: '', result: 'normal', orderId: 'o1' }],
      orders: ['', 'Laboratorio de rutina'],
    }, 'p1');
    expect(built).toEqual({ request: {
      id: 'id-1', patientId: 'p1', notes: 'Control.',
      items: [{ kind: 'PRESCRIPTION', description: 'enalapril 10 mg' }],
      checks: [{ description: 'ECG', result: null, orderId: null }, { description: null, result: 'normal', orderId: 'o1' }],
      orders: [{ description: 'Laboratorio de rutina' }],
    } });
  });

  it('refuses a check half filled rather than guessing it', () => {
    expect(toRequest({ ...draft, checks: [{ description: '', result: '72 lpm', orderId: '' }] }, 'p1'))
      .toEqual({ problem: expect.stringContaining('completá o vaciá los renglones a medias') });
  });

  it('knows whether anything was typed', () => {
    expect(isTouched(newDraft('x'))).toBe(false);
    expect(isTouched({ ...newDraft('x'), checks: [{ description: '', result: '', orderId: '' }] })).toBe(false);
    expect(isTouched({ ...newDraft('x'), orders: ['eco'] })).toBe(true);
    expect(isTouched({ ...newDraft('x'), notes: 'a' })).toBe(true);
  });

  it('sends a check without a consultation only with what was done or the order it fulfils', () => {
    expect(checkRequest({ description: ' ', result: 'x', orderId: '' }, 'p1', 'k'))
      .toEqual({ problem: 'Escribí qué estudio se hizo, o elegí la orden que cumple.' });
    expect(checkRequest({ description: '', result: '', orderId: 'o1' }, 'p1', 'k'))
      .toEqual({ request: { id: 'k', patientId: 'p1', description: null, result: null, orderId: 'o1' } });
  });
});

describe('the refusals of the record', () => {
  const refused = (status: number, code?: string) =>
    new ApiError(status, { status, error: 'x', message: 'x', ...(code ? { code } : {}) });

  it('says why a consultation was refused, by its code, and what Bean Validation refused by its status', () => {
    expect(describeError(refused(403, 'NOT_ARRIVED'), CONSULTATION_REFUSALS))
      .toBe('La consulta se registra cuando el paciente llegó a atenderse con vos hoy.');
    expect(describeError(refused(403, 'NO_ACCESS'), CONSULTATION_REFUSALS)).toBe('No podés leer esta historia clínica.');
    expect(describeError(refused(409, 'ORDER_NOT_OPEN'), CONSULTATION_REFUSALS)).toContain('ya no está abierta');
    expect(describeError(refused(400), CONSULTATION_REFUSALS)).toContain('hasta 10 000 caracteres');
  });

  it("says a file's refusals, the multipart limit's too", () => {
    expect(describeError(refused(415, 'UNSUPPORTED_FILE'), FILE_REFUSALS)).toBe('Solo se adjuntan archivos PDF, PNG o JPG.');
    expect(describeError(refused(413, 'FILE_TOO_LARGE'), FILE_REFUSALS)).toBe('El archivo supera los 10 MB.');
    expect(describeError(refused(413), FILE_REFUSALS)).toBe('El archivo supera los 10 MB.');
    expect(describeError(refused(409, 'RESULT_ALREADY_RECORDED'), RESULT_REFUSALS))
      .toBe('Ese estudio ya tiene resultado: se cargó recién.');
    expect(describeError(refused(413), RESULT_REFUSALS)).toBe('El archivo supera los 10 MB.');
  });
});
