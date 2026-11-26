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

// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({
  t: (key: string) => key,
  detail: vi.fn(), content: vi.fn(),
}));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: mocks.t }) }));
vi.mock('@/api/hub-resource', () => ({
  hubResourceApi: { detail: mocks.detail, content: mocks.content },
  sourceResourcePath: () => null,
  resourceListPath: () => '/mcpServerManagement/shared/list',
  resourceTypeForPath: () => 'mcp',
}));
vi.mock('@/stores/auth-store', () => ({ useAuthStore: (selector: (state: { isAuthenticated: boolean; globalAdmin: boolean }) => unknown) => selector({ isAuthenticated: false, globalAdmin: false }) }));
vi.mock('@/components/ai/hub/ResourceTransferDialog', () => ({ ResourceTransferDialog: () => null }));
vi.mock('@/api/agent', () => ({ agentApi: { getRuntimeEndpoints: vi.fn() } }));
vi.mock('@/stores/server-store', () => ({ useServerStore: (selector: (state: { copilotEnabled: boolean }) => unknown) => selector({ copilotEnabled: false }) }));
vi.mock('@monaco-editor/react', () => ({ Editor: () => null }));
vi.mock('@/lib/hub-resource-downloads', () => ({ resourceDefinition: () => ({}), resourceDownloadName: () => '' }));
import HubResourceDetailPage from '../index';
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const container = document.createElement('div');
document.body.append(container);
let root = createRoot(container);
afterEach(async () => {
  await act(async () => root.unmount());
  root = createRoot(container);
  vi.clearAllMocks();
});
async function open(labels: Record<string, string>) {
  mocks.detail.mockResolvedValue({ resourceType: 'mcp', hubEntryId: '501', resourceName: 'source-mcp', version: '1.0.0', versionLabels: [] });
  mocks.content.mockResolvedValue({ namespaceId: '__nacos_ai_hub__', mcpName: '501', version: '1.0.0', labels, serverSpecification: { name: '501', protocol: 'stdio', enabled: true } });
  await act(async () => root.render(<MemoryRouter initialEntries={['/mcpServerManagement/shared/detail/501']}><Routes><Route path="/mcpServerManagement/shared/detail/:hubEntryId" element={<HubResourceDetailPage />} /></Routes></MemoryRouter>));
}
describe('MCP Hub fixed-version label display', () => {
  it('shows only pinned custom label names in the right column below governance', async () => {
    await open({ latest: '1.0.0', stable: '1.0.0', production: '1.0.0', otherVersionLabel: '2.0.0' });
    const heading = container.querySelector('h1')!.parentElement!;
    expect(heading.textContent).toContain('source-mcp');
    expect(heading.textContent).not.toContain('1.0.0');
    expect(heading.textContent).not.toContain('stable');
    expect(heading.textContent).not.toContain('production');
    const headings = Array.from(container.querySelectorAll('h2'));
    const labels = headings.find(value => value.textContent === 'mcp.customLabels')!.closest('[data-slot=card]')!;
    const governance = headings.find(value => value.textContent === 'mcp.governance')!.closest('[data-slot=card]')!;
    expect(labels.parentElement).toBe(governance.parentElement);
    expect(governance.nextElementSibling).toBe(labels);
    expect(labels.textContent).toContain('stable');
    expect(labels.textContent).toContain('production');
    expect(labels.textContent).not.toContain('1.0.0');
    expect(labels.querySelector('textarea')).toBeNull();
    expect(labels.querySelector('button')).toBeNull();
    expect(heading.textContent).not.toContain('latest');
    expect(container.textContent).not.toContain('otherVersionLabel');
    expect(container.textContent).not.toContain('common.bizTags');
    expect(mocks.content).toHaveBeenCalledWith('mcp', '501', '1.0.0');
  });
  it('uses the Agent empty state in the right-side label card when no custom label matches', async () => {
    await open({ latest: '1.0.0', otherVersionLabel: '2.0.0' });
    expect(container.textContent).toContain('mcp.serverName');
    expect(container.textContent).not.toContain('otherVersionLabel');
    expect(container.textContent).toContain('mcp.customLabels');
    expect(container.textContent).toContain('common.versionLabels.noLabels');
    expect(container.textContent).not.toContain('common.bizTags');
  });
});
