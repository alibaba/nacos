/**
 * Prompt Store unit tests.
 * Verifies lifecycle actions auto-refresh governance detail after success.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGovernanceData = {
  schemaVersion: 1,
  promptKey: 'test-prompt',
  description: 'test',
  bizTags: [],
  bizTagsStr: '',
  latestVersion: '1.0.0',
  gmtModified: Date.now(),
  editingVersion: null,
  reviewingVersion: null,
  onlineCnt: 1,
  labels: {},
  versions: ['1.0.0'],
  versionDetails: [],
};

const mockPromptApi = {
  listPrompts: vi.fn().mockResolvedValue({ data: { pageItems: [], totalCount: 0 } }),
  getGovernanceDetail: vi.fn().mockResolvedValue({ data: mockGovernanceData }),
  getVersionDetail: vi.fn().mockResolvedValue({ data: { promptKey: 'test', version: '1.0.0', template: '', variables: [] } }),
  listVersions: vi.fn().mockResolvedValue({ data: { pageItems: [], totalCount: 0 } }),
  createDraft: vi.fn().mockResolvedValue({ data: '1.0.1' }),
  updateDraft: vi.fn().mockResolvedValue({ data: true }),
  deleteDraft: vi.fn().mockResolvedValue({ data: true }),
  submit: vi.fn().mockResolvedValue({ data: '1.0.0' }),
  publish: vi.fn().mockResolvedValue({ data: true }),
  forcePublish: vi.fn().mockResolvedValue({ data: true }),
  online: vi.fn().mockResolvedValue({ data: true }),
  offline: vi.fn().mockResolvedValue({ data: true }),
  updateLabels: vi.fn().mockResolvedValue({ data: true }),
  updateDescription: vi.fn().mockResolvedValue({ data: true }),
  updateBizTags: vi.fn().mockResolvedValue({ data: true }),
  deletePrompt: vi.fn().mockResolvedValue({ data: true }),
};

vi.mock('@/api/prompt', () => ({ promptApi: mockPromptApi }));

const { usePromptStore } = await import('../prompt-store');

describe('Prompt Store', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    usePromptStore.setState({
      currentGovernance: null,
      currentVersion: null,
      error: null,
    });
  });

  const lifecycleActions = [
    { name: 'createDraft', call: () => usePromptStore.getState().createDraft({ promptKey: 'test', namespaceId: 'public' }), apiMethod: 'createDraft' },
    { name: 'updateDraft', call: () => usePromptStore.getState().updateDraft({ promptKey: 'test', template: 'hi', namespaceId: 'public' }), apiMethod: 'updateDraft' },
    { name: 'deleteDraft', call: () => usePromptStore.getState().deleteDraft('public', 'test'), apiMethod: 'deleteDraft' },
    { name: 'submitVersion', call: () => usePromptStore.getState().submitVersion({ promptKey: 'test', namespaceId: 'public' }), apiMethod: 'submit' },
    { name: 'publishVersion', call: () => usePromptStore.getState().publishVersion({ promptKey: 'test', version: '1.0.0', namespaceId: 'public' }), apiMethod: 'publish' },
    { name: 'forcePublishVersion', call: () => usePromptStore.getState().forcePublishVersion({ promptKey: 'test', version: '1.0.0', namespaceId: 'public' }), apiMethod: 'forcePublish' },
    { name: 'onlineVersion', call: () => usePromptStore.getState().onlineVersion({ promptKey: 'test', version: '1.0.0', namespaceId: 'public' }), apiMethod: 'online' },
    { name: 'offlineVersion', call: () => usePromptStore.getState().offlineVersion({ promptKey: 'test', version: '1.0.0', namespaceId: 'public' }), apiMethod: 'offline' },
    { name: 'updateLabels', call: () => usePromptStore.getState().updateLabels({ promptKey: 'test', labels: '{}', namespaceId: 'public' }), apiMethod: 'updateLabels' },
    { name: 'updateDescription', call: () => usePromptStore.getState().updateDescription({ promptKey: 'test', description: 'new', namespaceId: 'public' }), apiMethod: 'updateDescription' },
    { name: 'updateBizTags', call: () => usePromptStore.getState().updateBizTags({ promptKey: 'test', bizTags: 'tag1', namespaceId: 'public' }), apiMethod: 'updateBizTags' },
  ];

  for (const { name, call, apiMethod } of lifecycleActions) {
    it(`${name} auto-refreshes governance detail on success`, async () => {
      await call();
      expect(mockPromptApi[apiMethod as keyof typeof mockPromptApi]).toHaveBeenCalled();
      expect(mockPromptApi.getGovernanceDetail).toHaveBeenCalled();
      expect(usePromptStore.getState().currentGovernance).toEqual(mockGovernanceData);
    });
  }
});
