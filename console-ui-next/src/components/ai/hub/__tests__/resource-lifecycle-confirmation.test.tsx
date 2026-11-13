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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { HubResourceType } from '@/api/hub-resource';
import zh from '@/locales/zh-CN.json';
import en from '@/locales/en-US.json';
import { ResourceHubActions } from '../ResourceHubActions';
import { useResourceLifecycleConfirmation } from '../ResourceLifecycleConfirmation';
import { ResourceDeleteWarning } from '../ResourceDeleteWarning';

const mocks = vi.hoisted(() => ({ sourceState: vi.fn(), mutate: vi.fn(), hubEnabled: true, authenticated: true }));
function translate(key: string, options: Record<string, string> = {}) {
  const [section, name] = key.split('.');
  const translated = (zh as Record<string, Record<string, unknown>>)[section]?.[name];
  const value = typeof translated === 'string' ? translated : key;
  return value.replace(/\{\{(\w+)\}\}/g, (_, field) => options[field] ?? '');
}
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: translate }) }));
vi.mock('@/api/hub-resource', () => ({ hubResourceApi: { sourceState: mocks.sourceState },
  resourceErrorMessage: (_: unknown, fallback: string) => fallback }));
vi.mock('@/stores/server-store', () => ({ useServerStore: (selector: (state: { hubEnabled: boolean }) => unknown) => selector({ hubEnabled: mocks.hubEnabled }) }));
vi.mock('@/stores/auth-store', () => ({ useAuthStore: (selector: (state: { isAuthenticated: boolean }) => unknown) => selector({ isAuthenticated: mocks.authenticated }) }));

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const container = document.createElement('div');
document.body.append(container);
let root = createRoot(container);
const activeShare = { hubEntryId: '500', metaVersion: '1', active: true, sharedVersion: '1.0.0' };

function Source({ type, namespaceId = 'source', name = 'example', version = '1.0.0' }: {
  type: HubResourceType; namespaceId?: string; name?: string; version?: string;
}) {
  const lifecycle = useResourceLifecycleConfirmation(type, namespaceId, name, version);
  return <>
    <ResourceHubActions type={type} namespaceId={namespaceId} name={name} version={version}
      status="online" scope="PUBLIC" onShareStateChange={lifecycle.onShareStateChange} />
    {(['offline', ...(type === 'prompt' ? [] : ['disable', 'private'])] as const).map(action =>
      <button key={action} data-action={action} onClick={async () => {
        if (await lifecycle.confirmLifecycle(action as 'offline' | 'disable' | 'private', action === 'offline' ? version : undefined)) {
          mocks.mutate(action, namespaceId, name, version);
        }
      }}>{action}</button>)}
    {lifecycle.lifecycleDialog}
  </>;
}
async function clickAction(action: string) {
  await act(async () => container.querySelector<HTMLButtonElement>(`[data-action="${action}"]`)!.click());
}
function dialogButton(label: string) {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button'))
    .find(button => button.textContent === label)!;
}
beforeEach(() => {
  mocks.hubEnabled = true;
  mocks.authenticated = true;
  mocks.mutate.mockReset();
  mocks.sourceState.mockReset().mockResolvedValue(activeShare);
});
afterEach(async () => {
  await act(async () => root.unmount());
  root = createRoot(container);
});

describe.each(['mcp', 'agent', 'prompt', 'agentspec'] as const)('%s shared-source lifecycle confirmation', type => {
  it('warns before taking the shared version offline and cancellation makes no mutation', async () => {
    await act(async () => root.render(<Source type={type} />));
    expect(mocks.sourceState).toHaveBeenCalledWith(type, 'source', 'example');
    await clickAction('offline');
    const dialog = document.querySelector('[role="dialog"]');
    expect(dialog?.textContent).toContain(`此操作可能立即撤销当前 ${zh.hubResource[type]} 共享，是否继续？`);
    expect(dialog?.textContent).toContain('版本下线 1.0.0');
    expect(mocks.mutate).not.toHaveBeenCalled();
    await act(async () => dialogButton('取消').click());
    expect(mocks.mutate).not.toHaveBeenCalled();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });
  it('performs the original operation exactly once after confirmation', async () => {
    await act(async () => root.render(<Source type={type} />));
    await clickAction('offline');
    const confirm = dialogButton('确认');
    await act(async () => { confirm.click(); confirm.click(); });
    expect(mocks.mutate).toHaveBeenCalledExactlyOnceWith('offline', 'source', 'example', '1.0.0');
  });
  it('does not warn when taking a different version offline', async () => {
    await act(async () => root.render(<Source type={type} version="2.0.0" />));
    await clickAction('offline');
    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(mocks.mutate).toHaveBeenCalledExactlyOnceWith('offline', 'source', 'example', '2.0.0');
  });
  it.each(['inactive', 'hub-disabled', 'anonymous'] as const)('preserves the original operation with %s sharing', async state => {
    if (state === 'inactive') mocks.sourceState.mockResolvedValue({ ...activeShare, active: false });
    if (state === 'hub-disabled') mocks.hubEnabled = false;
    if (state === 'anonymous') mocks.authenticated = false;
    await act(async () => root.render(<Source type={type} />));
    await clickAction('offline');
    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(mocks.mutate).toHaveBeenCalledOnce();
    if (state !== 'inactive') expect(mocks.sourceState).not.toHaveBeenCalled();
  });
  if (type !== 'prompt') {
    it.each(['disable', 'private'] as const)('warns for resource-wide %s even when viewing another version', async action => {
      await act(async () => root.render(<Source type={type} version="2.0.0" />));
      await clickAction(action);
      expect(document.querySelector('[role="dialog"]')?.textContent).toContain('共享，是否继续？');
      expect(mocks.mutate).not.toHaveBeenCalled();
      await act(async () => dialogButton('确认').click());
      expect(mocks.mutate).toHaveBeenCalledExactlyOnceWith(action, 'source', 'example', '2.0.0');
    });
  }
});

describe('confirmation context and deletion reminders', () => {
  it.each([{ namespaceId: 'other' }, { name: 'other' }, { version: '2.0.0' }])('cancels pending mutation when the page changes: %j', async next => {
    await act(async () => root.render(<Source type="agent" />));
    await clickAction('offline');
    await act(async () => root.render(<Source type="agent" {...next} />));
    expect(mocks.mutate).not.toHaveBeenCalled();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });
  it('cancels pending mutation if Hub is disabled', async () => {
    await act(async () => root.render(<Source type="agent" />));
    await clickAction('offline');
    mocks.hubEnabled = false;
    await act(async () => root.render(<Source type="agent" />));
    expect(mocks.mutate).not.toHaveBeenCalled();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });
  it('does not retain a warning after the sharing card refreshes to inactive', async () => {
    await act(async () => root.render(<Source type="agent" />));
    mocks.sourceState.mockResolvedValue({ ...activeShare, active: false });
    await act(async () => container.querySelector<HTMLButtonElement>('button[aria-label="刷新"]')!.click());
    await clickAction('offline');
    expect(mocks.mutate).toHaveBeenCalledOnce();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });
  it.each(['mcp', 'agent', 'prompt', 'agentspec'] as const)('renders the %s deletion reminder only when Hub is enabled', async type => {
    await act(async () => root.render(<ResourceDeleteWarning type={type} />));
    expect(container.textContent).toBe(`删除正在共享的 ${zh.hubResource[type]} 也会撤销其共享。`);
    mocks.hubEnabled = false;
    await act(async () => root.render(<ResourceDeleteWarning type={type} />));
    expect(container.textContent).toBe('');
  });
  it('provides corresponding Chinese and English lifecycle messages', () => {
    for (const key of ['lifecycleConfirm', 'offlineTitle', 'disableTitle', 'privateTitle', 'deleteWarning'] as const) {
      expect(zh.hubResource[key]).toBeTruthy();
      expect(en.hubResource[key]).toBeTruthy();
    }
  });
});
