import { describe, expect, it } from 'vitest';

import type { Entry } from '../arrivals/arrivals';
import { type MyQueue, queueActions, waitingNote } from './queue';

function entry(overrides: Partial<Entry> = {}): Entry {
  return {
    id: 'e1', patientId: 'p1', patient: 'Camila Molina', patientDocument: null, arrivedAt: '2026-03-10T11:20:00',
    emergency: false, appointmentId: null, appointmentAt: null, reservedForId: null, reservedFor: null, state: 'WAITING',
    timesCalled: 0, withPractitionerId: null, withPractitioner: null, ...overrides,
  };
}

function mine(overrides: Partial<MyQueue> = {}, open = true, waiting = [entry()]): MyQueue {
  return {
    queueId: 'q1', queueName: 'Consultorio 3', called: null,
    session: { queueId: 'q1', open, closesByItself: true, waiting, uncallable: [], called: [], done: [] },
    ...overrides,
  };
}

describe("the practitioner's queue", () => {
  it('offers calling the next while nobody is called, and says whether anybody waits', () => {
    expect(queueActions(mine())).toEqual({ finish: false, didNotComeIn: false, callNext: true, nobodyWaits: false });
    expect(queueActions(mine({}, true, [])).nobodyWaits).toBe(true);
  });

  it('offers finishing or saying they did not come in once somebody is called, and no other call', () => {
    expect(queueActions(mine({ called: entry({ state: 'CALLED' }) })))
      .toEqual({ finish: true, didNotComeIn: true, callNext: false, nobodyWaits: false });
  });

  it('offers nothing while the queue is closed', () => {
    expect(queueActions(mine({ called: entry() }, false)))
      .toEqual({ finish: false, didNotComeIn: false, callNext: false, nobodyWaits: false });
  });

  it("says a patient's appointment, or that they have none, and whom they wait for", () => {
    expect(waitingNote(entry())).toBe('Sin turno');
    expect(waitingNote(entry({ appointmentAt: '2026-03-10T10:00:00', reservedFor: 'Dra. Arias' })))
      .toBe('Turno 10:00 · espera a Dra. Arias');
  });
});
