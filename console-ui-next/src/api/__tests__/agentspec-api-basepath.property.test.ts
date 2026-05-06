/**
 * Property 13: API 基础路径一致性
 *
 * For any AgentSpec_API method call, the request URL SHALL use the base path
 * `v3/console/ai/agentspecs` as prefix.
 *
 * **Validates: Requirement 11.2**
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import fc from 'fast-check';

const EXPECTED_BASE = 'v3/console/ai/agentspecs';

// Capture all URLs passed to the axios client methods
const capturedUrls: string[] = [];

const mockClient = {
  get: vi.fn((...args: unknown[]) => {
    capturedUrls.push(args[0] as string);
    return Promise.resolve({ data: {} });
  }),
  post: vi.fn((...args: unknown[]) => {
    capturedUrls.push(args[0] as string);
    return Promise.resolve({ data: {} });
  }),
  put: vi.fn((...args: unknown[]) => {
    capturedUrls.push(args[0] as string);
    return Promise.resolve({ data: {} });
  }),
  delete: vi.fn((...args: unknown[]) => {
    capturedUrls.push(args[0] as string);
    return Promise.resolve({ data: {} });
  }),
};

vi.mock('../client', () => ({ default: mockClient }));

// Import after mock is set up
const { agentSpecApi } = await import('../agentspec');

/**
 * Helper: invoke every API method with minimal valid arguments and return
 * the list of URLs that were passed to the mocked axios client.
 */
async function callAllApiMethods(): Promise<string[]> {
  capturedUrls.length = 0;

  // Create a minimal File-like object for the upload method
  const fakeFile = new File(['zip-content'], 'test.zip', { type: 'application/zip' });

  await Promise.allSettled([
    agentSpecApi.list({ namespaceId: 'public' }),
    agentSpecApi.getDetail({ agentSpecName: 'test' }),
    agentSpecApi.getVersion({ agentSpecName: 'test', version: 'v1' }),
    agentSpecApi.delete({ agentSpecName: 'test' }),
    agentSpecApi.upload('public', fakeFile),
    agentSpecApi.createDraft({ agentSpecName: 'test' }),
    agentSpecApi.updateDraft({ agentSpecCard: '{"name":"test"}' }),
    agentSpecApi.deleteDraft({ agentSpecName: 'test' }),
    agentSpecApi.submit({ agentSpecName: 'test', version: 'v1' }),
    agentSpecApi.publish({ agentSpecName: 'test', version: 'v1' }),
    agentSpecApi.updateLabels({ agentSpecName: 'test', labels: '{}' }),
    agentSpecApi.updateBizTags({ agentSpecName: 'test', bizTags: '[]' }),
    agentSpecApi.online({ agentSpecName: 'test' }),
    agentSpecApi.offline({ agentSpecName: 'test' }),
  ]);

  return [...capturedUrls];
}

describe('Property 13: API 基础路径一致性', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedUrls.length = 0;
  });

  it('all agentSpecApi methods use the correct base path prefix', async () => {
    const urls = await callAllApiMethods();

    expect(urls.length).toBe(14);

    // Every URL must start with the expected base path
    for (const url of urls) {
      expect(url).toMatch(new RegExp(`^${EXPECTED_BASE}`));
    }
  });

  it('property: for any randomly selected subset of API methods, all URLs start with the base path', async () => {
    // Define all API method invocations as thunks
    const fakeFile = new File(['zip'], 'a.zip', { type: 'application/zip' });

    const apiMethods: Array<{ name: string; call: () => Promise<unknown> }> = [
      { name: 'list', call: () => agentSpecApi.list({}) },
      { name: 'getDetail', call: () => agentSpecApi.getDetail({ agentSpecName: 'x' }) },
      { name: 'getVersion', call: () => agentSpecApi.getVersion({ agentSpecName: 'x', version: 'v1' }) },
      { name: 'delete', call: () => agentSpecApi.delete({ agentSpecName: 'x' }) },
      { name: 'upload', call: () => agentSpecApi.upload('ns', fakeFile) },
      { name: 'createDraft', call: () => agentSpecApi.createDraft({ agentSpecName: 'x' }) },
      { name: 'updateDraft', call: () => agentSpecApi.updateDraft({ agentSpecCard: '{"name":"x"}' }) },
      { name: 'deleteDraft', call: () => agentSpecApi.deleteDraft({ agentSpecName: 'x' }) },
      { name: 'submit', call: () => agentSpecApi.submit({ agentSpecName: 'x', version: 'v1' }) },
      { name: 'publish', call: () => agentSpecApi.publish({ agentSpecName: 'x', version: 'v1' }) },
      { name: 'updateLabels', call: () => agentSpecApi.updateLabels({ agentSpecName: 'x', labels: '{}' }) },
      { name: 'updateBizTags', call: () => agentSpecApi.updateBizTags({ agentSpecName: 'x', bizTags: '[]' }) },
      { name: 'online', call: () => agentSpecApi.online({ agentSpecName: 'x' }) },
      { name: 'offline', call: () => agentSpecApi.offline({ agentSpecName: 'x' }) },
    ];

    await fc.assert(
      fc.asyncProperty(
        // Generate a non-empty subset of method indices
        fc.uniqueArray(fc.integer({ min: 0, max: apiMethods.length - 1 }), { minLength: 1 }),
        async (indices) => {
          capturedUrls.length = 0;
          vi.clearAllMocks();

          // Call the selected subset of methods
          await Promise.allSettled(indices.map((i) => apiMethods[i].call()));

          // Every captured URL must start with the expected base path
          expect(capturedUrls.length).toBe(indices.length);
          for (const url of capturedUrls) {
            expect(url.startsWith(EXPECTED_BASE)).toBe(true);
          }
        },
      ),
      { numRuns: 50 },
    );
  });
});
