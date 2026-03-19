/**
 * Property 1: 版本状态机转换合法性
 *
 * For any version status and lifecycle action, the VersionTimeline SHALL only
 * enable actions that are valid transitions according to the state machine:
 * draft → reviewing → online ↔ offline. Specifically, for any version status
 * value, the set of enabled action buttons must exactly match the allowed
 * transitions from that status.
 *
 * **Validates: Requirements 5.2, 5.3, 5.4, 5.5, 5.6**
 */
import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import { getValidActions } from '../version-utils';

// ── Expected state machine definition ──────────────────────────────────────

const EXPECTED_ACTIONS: Record<string, string[]> = {
  draft: ['submit', 'deleteDraft'],
  reviewing: ['publish'],
  online: ['offline'],
  offline: ['online'],
};

const KNOWN_STATUSES = Object.keys(EXPECTED_ACTIONS);

// ── Arbitrary generators ───────────────────────────────────────────────────

/** Generate one of the known version statuses */
const arbKnownStatus = fc.constantFrom(...KNOWN_STATUSES);

/** Generate an arbitrary unknown status string (not in the known set) */
const arbUnknownStatus = fc
  .string({ minLength: 1, maxLength: 30 })
  .filter((s) => !KNOWN_STATUSES.includes(s));

// ── Tests ──────────────────────────────────────────────────────────────────

describe('Property 1: 版本状态机转换合法性', () => {
  it('property: draft status yields exactly [submit, deleteDraft]', () => {
    fc.assert(
      fc.property(fc.constant('draft'), (status) => {
        const actions = getValidActions(status);
        expect(actions).toEqual(['submit', 'deleteDraft']);
      }),
      { numRuns: 10 },
    );
  });

  it('property: reviewing status yields exactly [publish]', () => {
    fc.assert(
      fc.property(fc.constant('reviewing'), (status) => {
        const actions = getValidActions(status);
        expect(actions).toEqual(['publish']);
      }),
      { numRuns: 10 },
    );
  });

  it('property: online status yields exactly [offline]', () => {
    fc.assert(
      fc.property(fc.constant('online'), (status) => {
        const actions = getValidActions(status);
        expect(actions).toEqual(['offline']);
      }),
      { numRuns: 10 },
    );
  });

  it('property: offline status yields exactly [online]', () => {
    fc.assert(
      fc.property(fc.constant('offline'), (status) => {
        const actions = getValidActions(status);
        expect(actions).toEqual(['online']);
      }),
      { numRuns: 10 },
    );
  });

  it('property: any unknown status yields an empty array', () => {
    fc.assert(
      fc.property(arbUnknownStatus, (status) => {
        const actions = getValidActions(status);
        expect(actions).toEqual([]);
      }),
      { numRuns: 100 },
    );
  });

  it('property: getValidActions is deterministic (same input → same output)', () => {
    fc.assert(
      fc.property(fc.string({ minLength: 0, maxLength: 50 }), (status) => {
        const first = getValidActions(status);
        const second = getValidActions(status);
        expect(first).toEqual(second);
      }),
      { numRuns: 200 },
    );
  });

  it('property: for any known status, actions match the expected state machine', () => {
    fc.assert(
      fc.property(arbKnownStatus, (status) => {
        const actions = getValidActions(status);
        expect(actions).toEqual(EXPECTED_ACTIONS[status]);
      }),
      { numRuns: 100 },
    );
  });
});
