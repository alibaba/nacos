import { beforeEach, describe, expect, it, vi } from 'vitest';

const okResponse = () => Promise.resolve({ code: 0, message: 'success', data: {} });

const mockClient = {
  get: vi.fn<(...args: unknown[]) => Promise<object>>(okResponse),
  put: vi.fn<(...args: unknown[]) => Promise<object>>(okResponse),
};

vi.mock('../client', () => ({ default: mockClient }));

const { pluginApi } = await import('../plugin');

describe('Plugin API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads plugin detail with type and name parameters', async () => {
    await pluginApi.detail('auth', 'nacos');

    expect(mockClient.get).toHaveBeenCalledWith('v3/console/plugin', {
      params: { pluginType: 'auth', pluginName: 'nacos' },
    });
  });

  it('loads node availability with type and name parameters', async () => {
    await pluginApi.availability('auth', 'nacos');

    expect(mockClient.get).toHaveBeenCalledWith('v3/console/plugin/availability', {
      params: { pluginType: 'auth', pluginName: 'nacos' },
    });
  });

  it('sends a complete nested source map and local-only mode', async () => {
    const request = {
      pluginType: 'auth',
      pluginName: 'nacos',
      config: {
        'token.expire.seconds': '18000',
      },
      localOnly: true,
    };

    await pluginApi.updateConfig(request);

    expect(mockClient.put).toHaveBeenCalledWith('v3/console/plugin/config', request);
  });

  it('passes local-only state changes as a query parameter', async () => {
    const request = {
      pluginType: 'trace',
      pluginName: 'ai-resource-trace-log',
      enabled: false,
      localOnly: true,
    };

    await pluginApi.setStatus(request);

    expect(mockClient.put).toHaveBeenCalledWith(
      'v3/console/plugin/status',
      null,
      { params: request },
    );
  });
});
