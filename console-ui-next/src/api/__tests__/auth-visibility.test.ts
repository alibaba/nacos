import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockClient = {
  post: vi.fn(() => Promise.resolve({ data: 'ok' })),
  delete: vi.fn(() => Promise.resolve({ data: 'ok' })),
};

vi.mock('../client', () => ({ default: mockClient }));

const { authApi } = await import('../auth');

describe('auth visibility API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('grants visibility through the plugin-owned endpoint', async () => {
    const request = {
      namespaceId: 'public',
      resourceType: 'skill',
      resourceName: 'demo-skill',
      username: 'alice',
      action: 'r' as const,
    };

    await authApi.grantVisibility(request);

    expect(mockClient.post).toHaveBeenCalledWith('v3/auth/visibility', request);
  });

  it('revokes visibility through the plugin-owned endpoint', async () => {
    const request = {
      namespaceId: 'public',
      resourceType: 'agentspec',
      resourceName: 'demo-agent',
      username: 'alice',
      action: 'w' as const,
    };

    await authApi.revokeVisibility(request);

    expect(mockClient.delete).toHaveBeenCalledWith('v3/auth/visibility', {
      params: request,
    });
  });
});
