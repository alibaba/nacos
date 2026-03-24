/**
 * Property 10: 版本列表排序
 *
 * For any AgentSpecDetail returned by fetchDetail, the versions array SHALL be
 * sorted by updateTime in descending order (newest first).
 *
 * **Validates: Requirement 4.4**
 */
import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import type { AgentSpecVersionSummary } from '@/types/agentspec';
import { sortVersionsDescending } from '../version-utils';

// ── Arbitrary generators ───────────────────────────────────────────────────

const STATUSES = ['draft', 'reviewing', 'online', 'offline'] as const;

/** Generate a single AgentSpecVersionSummary with a random updateTime */
const arbVersionSummary: fc.Arbitrary<AgentSpecVersionSummary> = fc.record({
  version: fc.string({ minLength: 1, maxLength: 20 }),
  status: fc.constantFrom(...STATUSES),
  author: fc.string({ maxLength: 20 }),
  description: fc.string({ maxLength: 50 }),
  createTime: fc.nat({ max: 2_000_000_000_000 }),
  updateTime: fc.nat({ max: 2_000_000_000_000 }),
  publishPipelineInfo: fc.constantFrom(null, 'pipeline-1', 'pipeline-2'),
  downloadCount: fc.nat({ max: 1_000_000 }),
});

/** Generate an array of version summaries (0 to 50 items) */
const arbVersionList: fc.Arbitrary<AgentSpecVersionSummary[]> = fc.array(
  arbVersionSummary,
  { maxLength: 50 },
);

// ── Tests ──────────────────────────────────────────────────────────────────

describe('Property 10: 版本列表排序', () => {
  it('property: sorted versions are in descending updateTime order', () => {
    fc.assert(
      fc.property(arbVersionList, (versions) => {
        const sorted = sortVersionsDescending(versions);

        for (let i = 0; i < sorted.length - 1; i++) {
          expect(sorted[i].updateTime).toBeGreaterThanOrEqual(
            sorted[i + 1].updateTime,
          );
        }
      }),
      { numRuns: 200 },
    );
  });

  it('property: sorting preserves all elements (no items lost or added)', () => {
    fc.assert(
      fc.property(arbVersionList, (versions) => {
        const sorted = sortVersionsDescending(versions);

        expect(sorted.length).toBe(versions.length);

        // Every original element must appear in the sorted result
        for (const v of versions) {
          expect(sorted).toContain(v);
        }
      }),
      { numRuns: 200 },
    );
  });

  it('property: sorting is idempotent (sorting twice yields the same result)', () => {
    fc.assert(
      fc.property(arbVersionList, (versions) => {
        const once = sortVersionsDescending(versions);
        const twice = sortVersionsDescending(once);

        expect(twice.map((v) => v.updateTime)).toEqual(
          once.map((v) => v.updateTime),
        );
      }),
      { numRuns: 200 },
    );
  });

  it('property: sorting does not mutate the original array', () => {
    fc.assert(
      fc.property(arbVersionList, (versions) => {
        const original = [...versions];
        sortVersionsDescending(versions);

        expect(versions).toEqual(original);
      }),
      { numRuns: 100 },
    );
  });

  it('property: empty array remains empty after sorting', () => {
    const sorted = sortVersionsDescending([]);
    expect(sorted).toEqual([]);
  });

  it('property: single-element array is unchanged after sorting', () => {
    fc.assert(
      fc.property(arbVersionSummary, (version) => {
        const sorted = sortVersionsDescending([version]);
        expect(sorted.length).toBe(1);
        expect(sorted[0]).toBe(version);
      }),
      { numRuns: 50 },
    );
  });
});
