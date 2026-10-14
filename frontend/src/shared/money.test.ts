import { describe, expect, it } from 'vitest';

import { needsConfirming, parseAmount, pesos } from './money';
import { byCode } from './refusals';
import { ApiError } from './api';

describe('pesos', () => {
  it('are written as the practice reads them', () => {
    expect(pesos(15000)).toBe('$ 15.000,00');
    expect(pesos(2500.5)).toBe('$ 2.500,50');
    expect(pesos(1500000)).toBe('$ 1.500.000,00');
  });

  it("are read as the practice types them, a point for thousands and a comma for cents", () => {
    expect(parseAmount('15000')).toBe(15000);
    expect(parseAmount('15.000')).toBe(15000);
    expect(parseAmount('1.500.000')).toBe(1500000);
    expect(parseAmount('15.000,50')).toBe(15000.5);
    expect(parseAmount('15000,5')).toBe(15000.5);
    expect(parseAmount('$ 2.500,50')).toBe(2500.5);
    expect(parseAmount('150.5')).toBe(150.5);
  });

  it('are not an amount when empty, zero, with three decimals or not a number', () => {
    for (const typed of ['', '0', '0,00', '15,555', 'mil', '-5', '1.5.0', '15000.123']) {
      expect(parseAmount(typed)).toBeNull();
    }
  });

  /** The approved mock's threshold: above it, a second confirmation (D-100). */
  it('are confirmed twice only above $ 500.000', () => {
    expect(needsConfirming(500000)).toBe(false);
    expect(needsConfirming(500000.01)).toBe(true);
    expect(needsConfirming(15000)).toBe(false);
    expect(needsConfirming(null)).toBe(false);
  });
});

describe('a refusal by its code', () => {
  const words = byCode({ ALREADY_CHARGED: 'Esa visita ya está cobrada.' });

  it('is said in the words the screen has for it, and nothing for one it does not know', () => {
    expect(words(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'ALREADY_CHARGED' })))
      .toBe('Esa visita ya está cobrada.');
    expect(words(new ApiError(409, { status: 409, error: 'Conflict', message: 'x', code: 'OTHER' }))).toBeUndefined();
    expect(words(new ApiError(409, { status: 409, error: 'Conflict', message: 'x' }))).toBeUndefined();
    expect(words(new ApiError(502, null))).toBeUndefined();
  });
});
