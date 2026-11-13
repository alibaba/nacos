/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { HubSkillDetail } from '@/types/hub-skill';
import type { Namespace } from '@/api/namespace';
import { sourceSkillPath } from '@/lib/hub-skill-utils';

vi.mock('@/api', () => ({ namespaceApi: { list: vi.fn() } }));

const { useNamespaceStore } = await import('@/stores/namespace-store');
const originalState = useNamespaceStore.getState();

beforeEach(() => {
  useNamespaceStore.setState({
    currentNamespace: 'tenant-a', namespaceShowName: 'Tenant A',
    namespaces: [
      { namespace: 'public', namespaceShowName: 'public' },
      { namespace: 'tenant-a', namespaceShowName: 'Tenant A' },
      { namespace: 'tenant-b', namespaceShowName: 'Tenant B' },
    ] as Namespace[],
  });
});
afterEach(() => useNamespaceStore.setState(originalState, true));

describe('Skill detail namespace context', () => {
  it('aligns the header with the source namespace while preserving the pinned source URL', () => {
    const link = sourceSkillPath({ sourceReadable: true, sourceNamespaceId: 'public',
      sourceName: '2', sharedVersion: '0.0.1' } as HubSkillDetail)!;
    expect(link).toBe('/skill/2?namespaceId=public&version=0.0.1');
    useNamespaceStore.getState().syncNamespaceFromSearch(link.split('?')[1]);
    expect(useNamespaceStore.getState().currentNamespace).toBe('public');
    expect(useNamespaceStore.getState().namespaceShowName).toBe('public');
    expect(new URLSearchParams(link.split('?')[1]).get('version')).toBe('0.0.1');
  });

  it('uses the target namespace display name when opening an imported or copied draft', () => {
    useNamespaceStore.getState().syncNamespaceFromSearch('?namespaceId=tenant-b&version=0.0.1');
    expect(useNamespaceStore.getState().currentNamespace).toBe('tenant-b');
    expect(useNamespaceStore.getState().namespaceShowName).toBe('Tenant B');
  });

  it('resynchronizes in both directions for browser history and supports legacy namespace links', () => {
    const sync = useNamespaceStore.getState().syncNamespaceFromSearch;
    sync('?namespaceId=public&version=1.0.0');
    sync('?namespace=tenant-a&version=0.0.1');
    expect(useNamespaceStore.getState().currentNamespace).toBe('tenant-a');
    sync('?namespaceId=public&version=1.0.0');
    expect(useNamespaceStore.getState().currentNamespace).toBe('public');
  });

  it('follows the detail request namespaceId priority if both query aliases are present', () => {
    useNamespaceStore.getState().syncNamespaceFromSearch('?namespace=tenant-a&namespaceId=public');
    expect(useNamespaceStore.getState().currentNamespace).toBe('public');
  });

  it.each(['', '?version=0.0.1', '?namespaceId=', '?namespaceId=__nacos_ai_hub__',
    '?namespace=__nacos_ai_hub__'])('retains the business selection for an absent or internal namespace: %s', search => {
    useNamespaceStore.getState().syncNamespaceFromSearch(search);
    expect(useNamespaceStore.getState().currentNamespace).toBe('tenant-a');
    expect(useNamespaceStore.getState().namespaceShowName).toBe('Tenant A');
  });

  it('resolves a deep-link namespace label after the namespace list finishes loading', () => {
    useNamespaceStore.setState({ namespaces: [] });
    const sync = useNamespaceStore.getState().syncNamespaceFromSearch;
    sync('?namespaceId=tenant-b');
    expect(useNamespaceStore.getState().namespaceShowName).toBe('tenant-b');
    useNamespaceStore.setState({ namespaces: originalNamespaces() });
    sync('?namespaceId=tenant-b');
    expect(useNamespaceStore.getState().namespaceShowName).toBe('Tenant B');
  });

  it('does not emit another namespace change for version-only navigation in the same space', () => {
    const sync = useNamespaceStore.getState().syncNamespaceFromSearch;
    sync('?namespaceId=public&version=0.0.1');
    const listener = vi.fn();
    const unsubscribe = useNamespaceStore.subscribe(listener);
    try {
      sync('?namespaceId=public&version=1.0.0');
      expect(listener).not.toHaveBeenCalled();
    } finally { unsubscribe(); }
  });
});

function originalNamespaces(): Namespace[] {
  return [{ namespace: 'tenant-b', namespaceShowName: 'Tenant B' }] as Namespace[];
}
