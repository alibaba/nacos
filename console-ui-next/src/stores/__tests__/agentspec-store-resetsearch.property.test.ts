/**
 * Property 9: resetSearch 确定性
 *
 * For any store state (regardless of current searchName and pageNo values),
 * calling resetSearch SHALL always result in searchName being empty string
 * and pageNo being 1.
 *
 * **Validates: Requirement 12.4**
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

describe('Property 9: resetSearch 确定性', () => {
  beforeEach(() => {
    resetStore();
  });

  afterEach(() => {
    resetStore();
  });

  it('property: resetSearch always sets searchName to empty string and pageNo to 1', () => {
    fc.assert(
      fc.property(
        fc.string({ maxLength: 200 }),
        fc.integer({ min: 1, max: 10000 }),
        (randomSearchName, randomPageNo) => {
          // Set the store with random searchName and pageNo
          useAgentSpecStore.setState({
            searchName: randomSearchName,
            pageNo: randomPageNo,
          });

          // Call resetSearch
          useAgentSpecStore.getState().resetSearch();

          // Assert deterministic outcome
          const state = useAgentSpecStore.getState();
          expect(state.searchName).toBe('');
          expect(state.pageNo).toBe(1);
        },
      ),
      { numRuns: 100 },
    );
  });
});
