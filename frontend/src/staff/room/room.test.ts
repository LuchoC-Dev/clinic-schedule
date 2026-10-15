import { describe, expect, it } from 'vitest';

import { cardOf, counted, place } from './room';

describe("the waiting room's cards", () => {
  it('put whom the queue is seeing first, to go into its room, and then who waits in order', () => {
    expect(cardOf({ id: 'q', name: 'Dra. Romero', room: '2', open: true, called: ['Tomás Suárez'],
      waiting: ['Camila Méndez', 'Pedro Ibáñez', 'Lía Paz'] })).toEqual({
      title: 'Dra. Romero', room: '2', closed: false,
      inside: [{ name: 'Tomás Suárez', label: 'Pasá al consultorio 2' }],
      count: '3 esperando',
      waiting: [{ name: 'Camila Méndez', label: 'Sigue' }, { name: 'Pedro Ibáñez', label: '2°' }, { name: 'Lía Paz', label: '3°' }],
    });
  });

  it('send in without a room when the queue has none, each practitioner of a shared one', () => {
    expect(cardOf({ id: 'q', name: 'Guardia', room: null, open: true, called: ['Ana Pérez', 'NN-4821'], waiting: [] }).inside)
      .toEqual([{ name: 'Ana Pérez', label: 'Pasá' }, { name: 'NN-4821', label: 'Pasá' }]);
  });

  it('say a closed queue is closed, and nobody in it', () => {
    expect(cardOf({ id: 'q', name: 'Clínica', room: '3', open: false, called: ['x'], waiting: ['y'] }))
      .toEqual({ title: 'Clínica', room: '3', closed: true, inside: [], count: 'Cerrada', waiting: [] });
  });

  it('count who waits, and each one\'s place', () => {
    expect(counted(0)).toBe('Nadie esperando');
    expect(counted(1)).toBe('1 esperando');
    expect(counted(4)).toBe('4 esperando');
    expect([0, 1, 2, 9].map(place)).toEqual(['Sigue', '2°', '3°', '10°']);
  });
});
