/**
 * Property 7: 标签 key 验证
 *
 * For any label key string, the LabelEditor SHALL accept the key if and only if
 * it contains no special characters and is not already present in the current
 * labels map.
 *
 * **Validates: Requirements 8.2, 8.5**
 */
import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import { isReservedLabelKey, isValidLabelKey } from '../label-utils';

// ── Valid key pattern ──────────────────────────────────────────────────────

const VALID_KEY_PATTERN = /^[a-zA-Z0-9._-]+$/;

// ── Arbitrary generators ───────────────────────────────────────────────────

const VALID_CHARS = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-';

/** Generate a valid label key: non-empty, only alphanumeric + hyphen + underscore + dot */
const arbValidKey: fc.Arbitrary<string> = fc
  .array(fc.constantFrom(...VALID_CHARS.split('')), { minLength: 1, maxLength: 30 })
  .map((chars) => chars.join(''));

/** Generate a key that contains at least one special character */
const arbInvalidKey: fc.Arbitrary<string> = fc
  .tuple(
    fc.string({ minLength: 0, maxLength: 10 }),
    fc.constantFrom('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', ' ', '/', '\\', '=', '+', '?', '<', '>', ',', ';', ':', '"', "'", '`', '~', '[', ']', '{', '}', '|'),
    fc.string({ minLength: 0, maxLength: 10 }),
  )
  .map(([prefix, special, suffix]) => prefix + special + suffix)
  .filter((s) => s.length > 0 && !VALID_KEY_PATTERN.test(s));

/** Generate a list of unique valid keys to use as existingKeys */
const arbExistingKeys: fc.Arbitrary<string[]> = fc
  .uniqueArray(arbValidKey, { minLength: 0, maxLength: 10 });


// ── Tests ──────────────────────────────────────────────────────────────────

describe('Property 7: 标签 key 验证', () => {
  it('property: valid keys not in existingKeys are accepted', () => {
    fc.assert(
      fc.property(arbValidKey, arbExistingKeys, (key, existingKeys) => {
        fc.pre(!isReservedLabelKey(key));
        // Ensure the key is NOT in existingKeys for this test
        const filtered = existingKeys.filter((k) => k !== key);
        expect(isValidLabelKey(key, filtered)).toBe(true);
      }),
      { numRuns: 200 },
    );
  });

  it('property: keys with special characters are always rejected', () => {
    fc.assert(
      fc.property(arbInvalidKey, arbExistingKeys, (key, existingKeys) => {
        expect(isValidLabelKey(key, existingKeys)).toBe(false);
      }),
      { numRuns: 200 },
    );
  });

  it('property: keys already in existingKeys are rejected', () => {
    fc.assert(
      fc.property(arbValidKey, arbExistingKeys, (key, existingKeys) => {
        // Ensure the key IS in existingKeys
        const withKey = existingKeys.includes(key) ? existingKeys : [...existingKeys, key];
        expect(isValidLabelKey(key, withKey)).toBe(false);
      }),
      { numRuns: 200 },
    );
  });

  it('property: empty keys are always rejected', () => {
    fc.assert(
      fc.property(arbExistingKeys, (existingKeys) => {
        expect(isValidLabelKey('', existingKeys)).toBe(false);
      }),
      { numRuns: 100 },
    );
  });

  it('reserved latest label is rejected', () => {
    expect(isReservedLabelKey('latest')).toBe(true);
    expect(isReservedLabelKey('LATEST')).toBe(true);
    expect(isValidLabelKey('latest', [])).toBe(false);
  });

  it('property: isValidLabelKey is deterministic (same input → same output)', () => {
    fc.assert(
      fc.property(fc.string({ minLength: 0, maxLength: 30 }), arbExistingKeys, (key, existingKeys) => {
        const first = isValidLabelKey(key, existingKeys);
        const second = isValidLabelKey(key, existingKeys);
        expect(first).toBe(second);
      }),
      { numRuns: 200 },
    );
  });
});
