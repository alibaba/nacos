/**
 * Prompt API unit tests.
 * Verifies URL paths, HTTP methods, and parameter encoding for all lifecycle API methods.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';

const EXPECTED_BASE = 'v3/console/ai/prompt';

const capturedCalls: Array<{ method: string; url: string; data?: unknown; config?: unknown }> = [];

const mockClient = {
  get: vi.fn((...args: unknown[]) => {
    capturedCalls.push({ method: 'GET', url: args[0] as string, config: args[1] });
    return Promise.resolve({ data: {} });
  }),
  post: vi.fn((...args: unknown[]) => {
    capturedCalls.push({ method: 'POST', url: args[0] as string, data: args[1], config: args[2] });
    return Promise.resolve({ data: {} });
  }),
  put: vi.fn((...args: unknown[]) => {
    capturedCalls.push({ method: 'PUT', url: args[0] as string, data: args[1], config: args[2] });
    return Promise.resolve({ data: {} });
  }),
  delete: vi.fn((...args: unknown[]) => {
    capturedCalls.push({ method: 'DELETE', url: args[0] as string, config: args[1] });
    return Promise.resolve({ data: {} });
  }),
};

vi.mock('../client', () => ({ default: mockClient }));

const { promptApi } = await import('../prompt');

describe('Prompt API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedCalls.length = 0;
  });

  it('all API methods use the correct base path prefix', async () => {
    await Promise.allSettled([
      promptApi.listPrompts({}),
      promptApi.getGovernanceDetail({ promptKey: 'test' }),
      promptApi.getVersionDetail({ promptKey: 'test', version: '1.0.0' }),
      promptApi.listVersions({ promptKey: 'test' }),
      promptApi.createDraft({ promptKey: 'test' }),
      promptApi.updateDraft({ promptKey: 'test', template: 'hello' }),
      promptApi.deleteDraft({ promptKey: 'test' }),
      promptApi.submit({ promptKey: 'test' }),
      promptApi.publish({ promptKey: 'test', version: '1.0.0' }),
      promptApi.forcePublish({ promptKey: 'test', version: '1.0.0' }),
      promptApi.online({ promptKey: 'test', version: '1.0.0' }),
      promptApi.offline({ promptKey: 'test', version: '1.0.0' }),
      promptApi.updateLabels({ promptKey: 'test', labels: '{}' }),
      promptApi.updateDescription({ promptKey: 'test', description: 'desc' }),
      promptApi.updateBizTags({ promptKey: 'test', bizTags: 'tag1' }),
      promptApi.deletePrompt({ promptKey: 'test' }),
    ]);

    expect(capturedCalls.length).toBe(16);
    for (const call of capturedCalls) {
      expect(call.url).toMatch(new RegExp(`^${EXPECTED_BASE}`));
    }
  });

  it('listPrompts calls GET /list', async () => {
    await promptApi.listPrompts({ promptKey: 'test', namespaceId: 'public' });
    expect(mockClient.get).toHaveBeenCalledWith(`${EXPECTED_BASE}/list`, expect.objectContaining({ params: { promptKey: 'test', namespaceId: 'public' } }));
  });

  it('getGovernanceDetail calls GET /governance', async () => {
    await promptApi.getGovernanceDetail({ promptKey: 'test', namespaceId: 'ns1' });
    expect(mockClient.get).toHaveBeenCalledWith(`${EXPECTED_BASE}/governance`, expect.objectContaining({ params: { promptKey: 'test', namespaceId: 'ns1' } }));
  });

  it('getVersionDetail calls GET /version', async () => {
    await promptApi.getVersionDetail({ promptKey: 'test', version: '1.0.0', namespaceId: 'ns1' });
    expect(mockClient.get).toHaveBeenCalledWith(`${EXPECTED_BASE}/version`, expect.objectContaining({ params: { promptKey: 'test', version: '1.0.0', namespaceId: 'ns1' } }));
  });

  it('createDraft calls POST /draft with form-urlencoded', async () => {
    await promptApi.createDraft({ promptKey: 'test', template: 'hello {{name}}' });
    expect(mockClient.post).toHaveBeenCalledWith(
      `${EXPECTED_BASE}/draft`,
      expect.any(URLSearchParams),
      expect.objectContaining({ headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }),
    );
    const params = mockClient.post.mock.calls[0][1] as URLSearchParams;
    expect(params.get('promptKey')).toBe('test');
    expect(params.get('template')).toBe('hello {{name}}');
  });

  it('updateDraft calls PUT /draft with form-urlencoded', async () => {
    await promptApi.updateDraft({ promptKey: 'test', template: 'updated' });
    expect(mockClient.put).toHaveBeenCalledWith(
      `${EXPECTED_BASE}/draft`,
      expect.any(URLSearchParams),
      expect.objectContaining({ headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }),
    );
  });

  it('deleteDraft calls DELETE /draft', async () => {
    await promptApi.deleteDraft({ promptKey: 'test', namespaceId: 'ns1' });
    expect(mockClient.delete).toHaveBeenCalledWith(`${EXPECTED_BASE}/draft`, expect.objectContaining({ params: { promptKey: 'test', namespaceId: 'ns1' } }));
  });

  it('submit calls POST /submit', async () => {
    await promptApi.submit({ promptKey: 'test', version: '1.0.0' });
    expect(mockClient.post).toHaveBeenCalledWith(`${EXPECTED_BASE}/submit`, expect.any(URLSearchParams), expect.any(Object));
  });

  it('publish calls POST /publish', async () => {
    await promptApi.publish({ promptKey: 'test', version: '1.0.0' });
    expect(mockClient.post).toHaveBeenCalledWith(`${EXPECTED_BASE}/publish`, expect.any(URLSearchParams), expect.any(Object));
    const params = mockClient.post.mock.calls[0][1] as URLSearchParams;
    expect(params.has('updateLatestLabel')).toBe(false);
  });

  it('forcePublish calls POST /force-publish', async () => {
    await promptApi.forcePublish({ promptKey: 'test', version: '1.0.0' });
    expect(mockClient.post).toHaveBeenCalledWith(`${EXPECTED_BASE}/force-publish`, expect.any(URLSearchParams), expect.any(Object));
  });

  it('online calls POST /online', async () => {
    await promptApi.online({ promptKey: 'test', version: '1.0.0' });
    expect(mockClient.post).toHaveBeenCalledWith(`${EXPECTED_BASE}/online`, expect.any(URLSearchParams), expect.any(Object));
  });

  it('offline calls POST /offline', async () => {
    await promptApi.offline({ promptKey: 'test', version: '1.0.0' });
    expect(mockClient.post).toHaveBeenCalledWith(`${EXPECTED_BASE}/offline`, expect.any(URLSearchParams), expect.any(Object));
  });

  it('updateLabels calls PUT /labels', async () => {
    await promptApi.updateLabels({ promptKey: 'test', labels: '{"latest":"1.0.0"}' });
    expect(mockClient.put).toHaveBeenCalledWith(`${EXPECTED_BASE}/labels`, expect.any(URLSearchParams), expect.any(Object));
  });

  it('updateDescription calls PUT /description', async () => {
    await promptApi.updateDescription({ promptKey: 'test', description: 'new desc' });
    expect(mockClient.put).toHaveBeenCalledWith(`${EXPECTED_BASE}/description`, expect.any(URLSearchParams), expect.any(Object));
  });

  it('updateBizTags calls PUT /biz-tags', async () => {
    await promptApi.updateBizTags({ promptKey: 'test', bizTags: 'tag1,tag2' });
    expect(mockClient.put).toHaveBeenCalledWith(`${EXPECTED_BASE}/biz-tags`, expect.any(URLSearchParams), expect.any(Object));
  });

  it('deletePrompt calls DELETE /', async () => {
    await promptApi.deletePrompt({ promptKey: 'test' });
    expect(mockClient.delete).toHaveBeenCalledWith(EXPECTED_BASE, expect.any(Object));
  });
});
