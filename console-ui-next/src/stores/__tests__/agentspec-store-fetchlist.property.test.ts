/**
 * Property 8: fetchList loading 状态转换
 *
 * For any fetchList call (success or failure), the AgentSpec_Store SHALL
 * transition loading from true to false. On success, items SHALL equal the
 * response pageItems and total SHALL equal the response totalCount. On failure,
 * items SHALL be an empty array and error SHALL contain the error message.
 *
 * **Validates: Requirements 12.2, 12.3, 12.6**
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import fc from 'fast-check';
import type { AgentSpecListItem } from '@/types/agentspec';

// ── Mock agentSpecApi.list before importing the store ──────────────────────
const mockList = vi.fn();

vi.mock('@/api/agentspec', () => ({
  agentSpecApi: {
    list: (...args: unknown[]) => mockList(...args),
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

/** Generate a valid AgentSpecListItem */
const arbListItem: fc.Arbitrary<AgentSpecListItem> = fc.record({
  namespaceId: fc.string({ minLength: 1, maxLength: 20 }),
  name: fc.string({ minLength: 1, maxLength: 50 }),
  description: fc.string({ maxLength: 100 }),
  enable: fc.boolean(),
  scope: fc.constantFrom('PUBLIC', 'PRIVATE'),
  bizTags: fc.constant('[]'),
  from: fc.constantFrom('local', 'import', 'sync'),
  labels: fc.constant({}),
  editingVersion: fc.option(fc.string({ minLength: 1, maxLength: 10 }), { nil: null }),
  reviewingVersion: fc.option(fc.string({ minLength: 1, maxLength: 10 }), { nil: null }),
  onlineCnt: fc.nat({ max: 100 }),
  updateTime: fc.nat(),
  downloadCount: fc.nat(),
});

/** Generate a successful API response with random pageItems and totalCount */
const arbSuccessResponse = fc.record({
  totalCount: fc.integer({ min: 0, max: 10000 }),
  pageItems: fc.array(arbListItem, { maxLength: 20 }),
});

/** Generate an error message for failure scenarios */
const arbErrorMessage = fc.string({ minLength: 1, maxLength: 200 });

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

describe('Property 8: fetchList loading 状态转换', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetStore();
  });

  afterEach(() => {
    resetStore();
  });

  it('property: on success, loading transitions true→false, items and total match response', async () => {
    await fc.assert(
      fc.asyncProperty(arbSuccessResponse, async ({ totalCount, pageItems }) => {
        resetStore();
        mockList.mockResolvedValueOnce({ data: { totalCount, pageItems } });

        // Capture loading=true during the call
        let loadingDuringFetch = false;
        const unsubscribe = useAgentSpecStore.subscribe((state) => {
          if (state.loading) loadingDuringFetch = true;
        });

        await useAgentSpecStore.getState().fetchList('public');
        unsubscribe();

        const state = useAgentSpecStore.getState();

        // loading was set to true during the fetch
        expect(loadingDuringFetch).toBe(true);
        // loading is false after completion
        expect(state.loading).toBe(false);
        // items match the response pageItems
        expect(state.items).toEqual(pageItems);
        // total matches the response totalCount
        expect(state.total).toBe(totalCount);
        // no error on success
        expect(state.error).toBeNull();
      }),
      { numRuns: 50 },
    );
  });

  it('property: on failure, loading transitions true→false, items is empty, error is set', async () => {
    await fc.assert(
      fc.asyncProperty(arbErrorMessage, async (errorMsg) => {
        resetStore();

        // Simulate an AxiosError-like rejection
        const axiosError = {
          response: { data: { message: errorMsg } },
          message: errorMsg,
        };
        mockList.mockRejectedValueOnce(axiosError);

        let loadingDuringFetch = false;
        const unsubscribe = useAgentSpecStore.subscribe((state) => {
          if (state.loading) loadingDuringFetch = true;
        });

        await useAgentSpecStore.getState().fetchList('public');
        unsubscribe();

        const state = useAgentSpecStore.getState();

        // loading was set to true during the fetch
        expect(loadingDuringFetch).toBe(true);
        // loading is false after completion
        expect(state.loading).toBe(false);
        // items is empty on failure
        expect(state.items).toEqual([]);
        // total is 0 on failure
        expect(state.total).toBe(0);
        // error contains the error message
        expect(state.error).toBe(errorMsg);
      }),
      { numRuns: 50 },
    );
  });
});
