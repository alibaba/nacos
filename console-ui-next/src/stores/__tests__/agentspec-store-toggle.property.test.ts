/**
 * Property 2: 选择状态 toggle 幂等性
 *
 * For any AgentSpec name and any initial selectedNames set, calling
 * toggleSelect twice with the same name SHALL return the selectedNames
 * set to its original state.
 *
 * **Validates: Requirement 12.5**
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import fc from 'fast-check';

// ── Mock @/api/agentspec before importing the store ────────────────────────
vi.mock('@/api/agentspec', () => ({
  agentSpecApi: {
    list: vi.fn(),
    getDetail: vi.fn(),
    delete: vi.fn(),
    upload: vi.fn(),
    createDraft: vi.fn(),
    updateDraft: vi.fn(),
    deleteDraft: vi.fn(),
    submit: vi.fn(),
    publish: vi.fn(),
    updateLabels: vi.fn(),
    online: vi.fn(),
    offline: vi.fn(),
  },
}));

const { useAgentSpecStore } = await import('../agentspec-store');

// ── Arbitrary generators ───────────────────────────────────────────────────

/** Generate a non-empty name string (simulating AgentSpec names) */
const arbName = fc.string({ minLength: 1, maxLength: 50 });

/** Generate a set of unique names */
const arbNameSet = fc.uniqueArray(arbName, { maxLength: 20 }).map(
  (names) => new Set(names),
);

// ── Helpers ────────────────────────────────────────────────────────────────

function resetStore() {
  useAgentSpecStore.setState({
    items: [],
    loading: false,
    total: 0,
    pageNo: 1,
    pageSize: 12,
    searchName: '',
    selectedNames: new Set(),
    currentDetail: null,
    detailLoading: false,
    error: null,
  });
}

// ── Tests ──────────────────────────────────────────────────────────────────

describe('Property 2: 选择状态 toggle 幂等性', () => {
  beforeEach(() => {
    resetStore();
  });

  afterEach(() => {
    resetStore();
  });

  it('property: toggleSelect(name) twice returns selectedNames to its original state', () => {
    fc.assert(
      fc.property(arbNameSet, arbName, (initialSet, name) => {
        // Set the store's selectedNames to the initial set
        useAgentSpecStore.setState({ selectedNames: new Set(initialSet) });

        // Snapshot the original set
        const originalNames = new Set(initialSet);

        // Call toggleSelect twice with the same name
        useAgentSpecStore.getState().toggleSelect(name);
        useAgentSpecStore.getState().toggleSelect(name);

        // Assert selectedNames equals the original set
        const finalNames = useAgentSpecStore.getState().selectedNames;

        expect(finalNames.size).toBe(originalNames.size);
        for (const n of originalNames) {
          expect(finalNames.has(n)).toBe(true);
        }
        for (const n of finalNames) {
          expect(originalNames.has(n)).toBe(true);
        }
      }),
      { numRuns: 100 },
    );
  });
});
