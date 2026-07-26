/**
 * Property 3: 选择状态子集不变量
 *
 * For any store state, every name in selectedNames SHALL correspond to a name
 * present in the current items list. After any list refresh, selectedNames must
 * remain a subset of the names in items.
 *
 * **Validates: Requirements 2.1, 2.4**
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

/** Generate a non-empty name string */
const arbName = fc.string({ minLength: 1, maxLength: 30 });

/** Build an AgentSpecListItem from a given name */
function makeItem(name: string): AgentSpecListItem {
  return {
    namespaceId: 'public',
    name,
    description: '',
    enable: true,
    scope: 'PUBLIC',
    bizTags: '[]',
    from: 'local',
    labels: {},
    editingVersion: null,
    reviewingVersion: null,
    onlineCnt: 0,
    updateTime: 0,
    downloadCount: 0,
  };
}

/**
 * Generate a tuple of:
 *  - itemNames: unique names that will appear in the API response
 *  - selectedNames: a mix of names — some from itemNames, some not
 */
const arbScenario = fc
  .uniqueArray(arbName, { minLength: 1, maxLength: 15 })
  .chain((itemNames) =>
    fc
      .uniqueArray(
        fc.oneof(
          fc.constantFrom(...itemNames),   // names present in items
          arbName,                          // names possibly NOT in items
        ),
        { maxLength: 20 },
      )
      .map((selected) => ({ itemNames, selectedNames: selected })),
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

describe('Property 3: 选择状态子集不变量', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetStore();
  });

  afterEach(() => {
    resetStore();
  });

  it('property: after fetchList, every name in selectedNames is present in items', async () => {
    await fc.assert(
      fc.asyncProperty(arbScenario, async ({ itemNames, selectedNames }) => {
        resetStore();

        // Pre-set selectedNames with a mix of valid and invalid names
        useAgentSpecStore.setState({
          selectedNames: new Set(selectedNames),
        });

        // Mock the API to return items with the given names
        const pageItems = itemNames.map(makeItem);
        mockList.mockResolvedValueOnce({
          data: { totalCount: pageItems.length, pageItems },
        });

        // Call fetchList — this should prune selectedNames
        await useAgentSpecStore.getState().fetchList('public');

        const state = useAgentSpecStore.getState();
        const itemNameSet = new Set(itemNames);

        // Invariant: every name in selectedNames must exist in items
        for (const name of state.selectedNames) {
          expect(itemNameSet.has(name)).toBe(true);
        }
      }),
      { numRuns: 100 },
    );
  });
});
