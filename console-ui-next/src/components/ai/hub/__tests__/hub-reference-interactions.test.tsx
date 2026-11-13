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
import { afterEach, describe, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({ runtime: vi.fn(), t: (key: string) => key }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: mocks.t }) }));
vi.mock('@/api/agent', () => ({ agentApi: { getRuntimeEndpoints: mocks.runtime } }));
vi.mock('@/stores/server-store', () => ({ useServerStore: (selector: (value: { copilotEnabled: boolean }) => unknown) => selector({ copilotEnabled: true }) }));
import { HubAgentContent } from '../HubAgentContent';
import { PromptDebugPanel } from '@/components/ai/PromptDebugPanel';
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const container = document.createElement('div');
document.body.append(container);
let root = createRoot(container);
afterEach(async () => {
  await act(async () => root.unmount());
  root = createRoot(container);
  vi.restoreAllMocks();
});
describe('Hub source-reference interactions', () => {
  it('loads and refreshes the original runtime endpoints through the explicit Hub ID', async () => {
    mocks.runtime.mockResolvedValue({ code: 0, data: { runtimeEndpointSnapshot: { namespaceId: 'source-ns', agentName: 'source-agent', version: '1.0.0', callInterface: { endpointSets: [{ lastUpdatedTime: 1700000000000, endpoints: [{ uri: 'https://agent.example/runtime', transport: 'HTTP+JSON', healthy: false, enabled: true, bindings: [{ runtimeVersion: '1.0.0', versionRange: '^1' }] }] }] } } } });
    await act(async () => root.render(<HubAgentContent hubEntryId="501" content={{ version: '1.0.0', callInterfaces: [{ protocol: 'a2a', protocolVersion: '0.3', endpointSets: [] }] }} />));
    expect(mocks.runtime).toHaveBeenCalledWith({ namespaceId: '__nacos_ai_hub__', agentName: '501', version: '1.0.0', protocol: 'a2a' });
    expect(container.textContent).toContain('https://agent.example/runtime');
    expect(container.textContent).toContain('UNHEALTHY');
    expect(container.textContent).toContain('1.0.0 → ^1');
    const before = mocks.runtime.mock.calls.length;
    await act(async () => Array.from(container.querySelectorAll('button')).find(value => value.textContent?.includes('common.refresh'))!.click());
    expect(mocks.runtime.mock.calls.length).toBe(before + 1);
  });
  it('keeps Prompt debug interactive without writing the referenced resource', async () => {
    const request = vi.fn().mockResolvedValue({ ok: true, body: { getReader: () => ({ read: async () => ({ done: true }) }) } });
    vi.stubGlobal('fetch', request);
    await act(async () => root.render(<PromptDebugPanel template="Hello {{name}}" versionInfo={{ variables: [{ name: 'name', defaultValue: 'Guest', description: 'name' }] }} />));
    expect(container.textContent).toContain('prompt.debugPanel');
    expect(container.textContent).toContain('prompt.modelOutput');
    expect(request).not.toHaveBeenCalled();
    const input = container.querySelector('textarea')!;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(input, 'Question');
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () => Array.from(container.querySelectorAll('button')).find(value => value.textContent?.includes('prompt.startDebug'))!.click());
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.calls[0][0]).toContain('/v3/console/copilot/prompt/debug');
    expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({ prompt: 'Hello Guest', userInput: 'Question' });
    vi.unstubAllGlobals();
  });
});
