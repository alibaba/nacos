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

  it('propagates validation failures from the visibility endpoint', async () => {
    const error = {
      response: {
        data: {
          code: 400,
          message: 'parameter validate error',
          data: "user 'missing-user' not found",
        },
      },
    };
    mockClient.post.mockRejectedValueOnce(error);

    await expect(authApi.grantVisibility({
      namespaceId: 'public',
      resourceType: 'skill',
      resourceName: 'demo-skill',
      username: 'missing-user',
      action: 'r',
    })).rejects.toBe(error);
  });

  it('propagates permission denial from the visibility endpoint', async () => {
    const error = {
      response: {
        data: {
          code: 403,
          message: 'access denied',
          data: 'No permission to manage visibility grants for resource: demo-agent',
        },
      },
    };
    mockClient.delete.mockRejectedValueOnce(error);

    await expect(authApi.revokeVisibility({
      namespaceId: 'public',
      resourceType: 'agentspec',
      resourceName: 'demo-agent',
      username: 'alice',
      action: 'w',
    })).rejects.toBe(error);
  });

  it('propagates unsupported-plugin errors without using role or permission endpoints', async () => {
    const error = {
      response: {
        data: {
          code: 500,
          message: 'server error',
          data: 'visibility grant management is unsupported in current runtime',
        },
      },
    };
    mockClient.post.mockRejectedValueOnce(error);

    await expect(authApi.grantVisibility({
      namespaceId: 'public',
      resourceType: 'skill',
      resourceName: 'demo-skill',
      username: 'alice',
      action: 'r',
    })).rejects.toBe(error);
    expect(mockClient.post).toHaveBeenCalledTimes(1);
    expect(mockClient.delete).not.toHaveBeenCalled();
  });
});
