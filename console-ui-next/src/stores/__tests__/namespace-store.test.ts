import { describe, expect, it, vi } from 'vitest';

vi.mock('@/api', () => ({
  namespaceApi: {
    list: vi.fn(),
  },
}));

const {
  getDefaultNamespaceFromHash,
  getNamespaceSearchAfterSwitch,
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
