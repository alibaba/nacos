import { describe, expect, it, vi } from 'vitest';

vi.mock('@/api', () => ({
  namespaceApi: {
    list: vi.fn(),
  },
}));

const {
  getDefaultNamespaceFromHash,
  getNamespaceSearchAfterSwitch,
  useNamespaceStore,
} = await import('../namespace-store');

describe('namespace store URL initialization', () => {
  it('reads legacy namespace query parameter', () => {
    expect(getDefaultNamespaceFromHash('#/skill?namespace=tenant-a')).toBe('tenant-a');
  });

  it('reads namespaceId query parameter used by AI detail pages', () => {
    expect(getDefaultNamespaceFromHash('#/skill/demo?namespaceId=tenant-a')).toBe('tenant-a');
  });
});

describe('getNamespaceSearchAfterSwitch', () => {
  it('updates namespaceId query used by AI detail routes', () => {
    expect(
      getNamespaceSearchAfterSwitch(
        '?namespaceId=namespace-a&version=0.0.1',
        'namespace-b',
        'Namespace B',
      ),
    ).toBe('?namespaceId=namespace-b&version=0.0.1');
  });

  it('updates legacy namespace and namespaceShowName query used by other routes', () => {
    expect(
      getNamespaceSearchAfterSwitch(
        '?namespace=namespace-a&namespaceShowName=Namespace+A',
        'namespace-b',
        'Namespace B',
      ),
    ).toBe('?namespace=namespace-b&namespaceShowName=Namespace+B');
  });

  it('keeps routes without namespace query controlled by the global store only', () => {
    expect(getNamespaceSearchAfterSwitch('?searchName=pdf', 'namespace-b', 'Namespace B')).toBeNull();
  });
});

describe('namespace change guard', () => {
  it('stores and clears a registered namespace change guard', () => {
    const guard = vi.fn(() => true);

    useNamespaceStore.getState().setNamespaceChangeGuard(guard);
    expect(useNamespaceStore.getState().getNamespaceChangeGuard()).toBe(guard);

    useNamespaceStore.getState().setNamespaceChangeGuard(null);
    expect(useNamespaceStore.getState().getNamespaceChangeGuard()).toBeNull();
  });
});


describe('Hub namespace isolation', () => {
  it('rejects a reserved namespace supplied by a legacy or detail deep link', () => {
    expect(getDefaultNamespaceFromHash('#/skill/demo?namespaceId=__nacos_ai_hub__')).toBe('');
    expect(getDefaultNamespaceFromHash('#/skill?namespace=__nacos_ai_hub__')).toBe('');
  });
  it('does not allow the ordinary namespace picker to select Hub', () => {
    useNamespaceStore.getState().setNamespace('tenant-a', 'Tenant A');
    useNamespaceStore.getState().setNamespace('__nacos_ai_hub__', 'Hub');
    expect(useNamespaceStore.getState().currentNamespace).toBe('tenant-a');
  });
  it('filters Hub from server namespace lists and retains the normal default', async () => {
    const { namespaceApi } = await import('@/api');
    vi.mocked(namespaceApi.list).mockResolvedValue({ code: 0, data: [
      { namespace: '__nacos_ai_hub__', namespaceShowName: 'Hub' },
      { namespace: 'public', namespaceShowName: 'Public' },
    ] } as unknown as Awaited<ReturnType<typeof namespaceApi.list>>);
    useNamespaceStore.setState({ currentNamespace: '' });
    await useNamespaceStore.getState().fetchNamespaces();
    expect(useNamespaceStore.getState().namespaces.map(ns => ns.namespace)).toEqual(['public']);
    expect(useNamespaceStore.getState().currentNamespace).toBe('public');
  });
  it('clears a stale reserved selection even when the server returns no ordinary namespaces', async () => {
    const { namespaceApi } = await import('@/api');
    vi.mocked(namespaceApi.list).mockResolvedValue({ code: 0, data: [] } as unknown as Awaited<ReturnType<typeof namespaceApi.list>>);
    useNamespaceStore.setState({ currentNamespace: '__nacos_ai_hub__', namespaceShowName: 'Hub' });
    await useNamespaceStore.getState().fetchNamespaces();
    expect(useNamespaceStore.getState().currentNamespace).toBe('');
    expect(useNamespaceStore.getState().namespaceShowName).toBe('');
  });

});
