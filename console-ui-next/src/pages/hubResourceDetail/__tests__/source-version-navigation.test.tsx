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
  fetchGovernance: vi.fn().mockResolvedValue(undefined),
  fetchPromptVersion: vi.fn().mockResolvedValue(undefined),
  fetchSpecDetail: vi.fn().mockResolvedValue(undefined),
  fetchSpecVersion: vi.fn().mockResolvedValue({ data: { content: '{}', resource: {} } }),
  clear: vi.fn(),
  promptMeta: { promptKey: 'source', labels: { latest: '2.0.0' }, versionDetails: [{ version: '2.0.0' }, { version: '1.0.0' }] },
}));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: mocks.t }) }));
vi.mock('@/locales', () => ({ default: { t: mocks.t } }));
vi.mock('@monaco-editor/react', () => ({ Editor: () => null }));
vi.mock('@/components/ai/hub/ResourceHubActions', () => ({ ResourceHubActions: () => null }));
vi.mock('@/stores/namespace-store', () => ({ useNamespaceStore: () => ({ currentNamespace: '__nacos_ai_hub__' }) }));
vi.mock('@/stores/auth-store', () => ({ useAuthStore: () => ({ globalAdmin: false, username: 'viewer' }) }));
vi.mock('@/stores/server-store', () => ({ useServerStore: (selector: (state: { copilotEnabled: boolean }) => unknown) => selector({ copilotEnabled: false }) }));
vi.mock('@/stores/prompt-store', () => ({ usePromptStore: () => ({
  currentGovernance: mocks.promptMeta,
  currentVersion: null,
  fetchGovernanceDetail: mocks.fetchGovernance,
  fetchVersionDetail: mocks.fetchPromptVersion,
  clearCurrentPrompt: mocks.clear,
}) }));
vi.mock('@/stores/agentspec-store', () => ({ useAgentSpecStore: () => ({
  currentDetail: null,
  detailLoading: true,
  fetchDetail: mocks.fetchSpecDetail,
  clearDetail: mocks.clear,
  clearError: mocks.clear,
}) }));
vi.mock('@/api/agentspec', () => ({ agentSpecApi: { getVersion: mocks.fetchSpecVersion } }));
import PromptDetailPage from '@/pages/promptDetail';
import AgentSpecDetailPage from '@/pages/agentSpecDetail';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const container = document.createElement('div');
document.body.append(container);
let root = createRoot(container);
afterEach(async () => {
  await act(async () => root.unmount());
  root = createRoot(container);
  vi.clearAllMocks();
});

describe('fixed source version landing pages', () => {
  it.each(['1.0.0', 'missing-version'])('Prompt requests %s instead of the first or latest version', async version => {
    await act(async () => root.render(<MemoryRouter initialEntries={[`/promptDetail?namespaceId=source-ns&promptKey=source&version=${version}`]}><PromptDetailPage /></MemoryRouter>));
    expect(mocks.fetchGovernance).toHaveBeenCalledWith('source-ns', 'source');
    expect(mocks.fetchPromptVersion).toHaveBeenCalledWith('source-ns', 'source', version);
    expect(mocks.fetchPromptVersion.mock.calls.every(call => call[2] === version)).toBe(true);
  });
  it.each(['1.0.0', 'missing-version'])('AgentSpec requests %s directly in the source namespace', async version => {
    await act(async () => root.render(<MemoryRouter initialEntries={[`/agentspec/source?namespaceId=source-ns&version=${version}`]}><Routes><Route path="/agentspec/:name" element={<AgentSpecDetailPage />} /></Routes></MemoryRouter>));
    expect(mocks.fetchSpecDetail).toHaveBeenCalledWith('source-ns', 'source');
    expect(mocks.fetchSpecVersion).toHaveBeenCalledWith({ namespaceId: 'source-ns', agentSpecName: 'source', version });
    expect(mocks.fetchSpecVersion.mock.calls.every(call => call[0].version === version)).toBe(true);
  });
});
