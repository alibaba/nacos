/**
 * Namespace API unit tests.
 * Verifies wire field names against backend NamespaceForm (namespaceId / namespaceName) and
 * CreateNamespaceForm (customNamespaceId / namespaceName). Regression coverage for #15141, where
 * the update path was sending `namespace` / `namespaceShowName` and the backend rejected the
 * request with "required parameter 'namespaceId' is missing".
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';

const EXPECTED_BASE = 'v3/console/core/namespace';

const okResponse = () => Promise.resolve({ data: {} });

const mockClient = {
  get: vi.fn<(...args: unknown[]) => Promise<{ data: object }>>(okResponse),
  post: vi.fn<(...args: unknown[]) => Promise<{ data: object }>>(okResponse),
  put: vi.fn<(...args: unknown[]) => Promise<{ data: object }>>(okResponse),
  delete: vi.fn<(...args: unknown[]) => Promise<{ data: object }>>(okResponse),
};

vi.mock('../client', () => ({ default: mockClient }));

const { namespaceApi } = await import('../namespace');

describe('Namespace API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('list calls GET /list', async () => {
    await namespaceApi.list();
    expect(mockClient.get).toHaveBeenCalledWith(`${EXPECTED_BASE}/list`);
  });

  it('detail calls GET with namespaceId param', async () => {
    await namespaceApi.detail('ns-1');
    expect(mockClient.get).toHaveBeenCalledWith(
      EXPECTED_BASE,
      expect.objectContaining({ params: { namespaceId: 'ns-1' } }),
    );
  });

  it('create POSTs the customNamespaceId / namespaceName wire field names', async () => {
    await namespaceApi.create({
      customNamespaceId: 'custom-id',
      namespaceName: 'dev',
      namespaceDesc: '调试',
    });
    expect(mockClient.post).toHaveBeenCalledWith(EXPECTED_BASE, {
      customNamespaceId: 'custom-id',
      namespaceName: 'dev',
      namespaceDesc: '调试',
    });
  });

  it('update PUTs the namespaceId / namespaceName wire field names', async () => {
    // Regression for #15141: the previous wire shape used `namespace` and `namespaceShowName`,
    // which fails the backend NamespaceForm#validate() with "required parameter 'namespaceId'
    // is missing".
    await namespaceApi.update({
      namespaceId: 'ns-1',
      namespaceName: 'dev',
      namespaceDesc: '调试',
    });
    expect(mockClient.put).toHaveBeenCalledWith(EXPECTED_BASE, {
      namespaceId: 'ns-1',
      namespaceName: 'dev',
      namespaceDesc: '调试',
    });
    const sentBody = mockClient.put.mock.calls[0]?.[1] as Record<string, unknown>;
    expect(sentBody?.namespace).toBeUndefined();
    expect(sentBody?.namespaceShowName).toBeUndefined();
  });

  it('remove DELETEs with namespaceId query string', async () => {
    await namespaceApi.remove('ns-1');
    expect(mockClient.delete).toHaveBeenCalledWith(`${EXPECTED_BASE}?namespaceId=ns-1`);
  });
});
