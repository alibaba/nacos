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

import { beforeEach, describe, expect, it, vi } from 'vitest';

const BASE = 'v3/console/ai/agents';
const mockClient = {
  get: vi.fn().mockResolvedValue({ data: {} }),
  post: vi.fn().mockResolvedValue({ data: {} }),
  put: vi.fn().mockResolvedValue({ data: {} }),
  delete: vi.fn().mockResolvedValue({ data: {} }),
};

vi.mock('../client', () => ({ default: mockClient }));

const { agentApi, toFormParams } = await import('../agent');

function expectForm(
  method: 'post' | 'put',
  path: string,
  expected: Record<string, string>,
): void {
  const call = mockClient[method].mock.calls.at(-1);
  expect(call?.[0]).toBe(`${BASE}${path}`);
  expect(call?.[1]).toBeInstanceOf(URLSearchParams);
  expect(Object.fromEntries((call?.[1] as URLSearchParams).entries())).toEqual(expected);
  expect(call?.[2]).toEqual({
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
}

describe('Agent Console API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('encodes only present form fields while preserving empty, zero and false values', () => {
    expect(Object.fromEntries(toFormParams({
      omitted: undefined,
      absent: null,
      empty: '',
      count: 0,
      enabled: false,
    }).entries())).toEqual({
      empty: '',
      count: '0',
      enabled: 'false',
    });
  });

  it('uses exact read and delete routes with query parameters', async () => {
    const list = { namespaceId: 'public', agentName: 'demo', pageNo: 2, pageSize: 20 };
    const identity = { namespaceId: 'public', agentName: 'demo' };
    const version = { ...identity, version: '1.0.0' };
    await agentApi.listAgents(list);
    expect(mockClient.get).toHaveBeenLastCalledWith(`${BASE}/list`, { params: list });
    await agentApi.getAgent(identity);
    expect(mockClient.get).toHaveBeenLastCalledWith(BASE, { params: identity });
    await agentApi.listVersions({ ...identity, status: 'online', pageNo: 1, pageSize: 10 });
    expect(mockClient.get).toHaveBeenLastCalledWith(`${BASE}/versions`, {
      params: { ...identity, status: 'online', pageNo: 1, pageSize: 10 },
    });
    await agentApi.getVersion(version);
    expect(mockClient.get).toHaveBeenLastCalledWith(`${BASE}/version`, { params: version });
    await agentApi.getRuntimeEndpoints({ ...version, protocol: 'A2A' });
    expect(mockClient.get).toHaveBeenLastCalledWith(`${BASE}/runtime-endpoints`, {
      params: { ...version, protocol: 'A2A' },
    });
    await agentApi.deleteAgent(identity);
    expect(mockClient.delete).toHaveBeenLastCalledWith(BASE, { params: identity });
    await agentApi.deleteDraft(version);
    expect(mockClient.delete).toHaveBeenLastCalledWith(`${BASE}/draft`, { params: version });
  });

  it('uses exact form routes for metadata, drafts and labels', async () => {
    await agentApi.updateAgent({
      namespaceId: 'public',
      agentName: 'demo',
      status: 'enable',
      tags: '["tag"]',
    });
    expectForm('put', '', {
      namespaceId: 'public',
      agentName: 'demo',
      status: 'enable',
      tags: '["tag"]',
    });

    await agentApi.createDraft({
      namespaceId: 'public',
      agentName: 'demo',
      version: '1.0.0',
      callInterfaces: '[]',
    });
    expectForm('post', '/draft', {
      namespaceId: 'public',
      agentName: 'demo',
      version: '1.0.0',
      callInterfaces: '[]',
    });

    await agentApi.updateDraft({
      namespaceId: 'public',
      agentName: 'demo',
      version: '1.0.0',
      callInterfaces: '[{}]',
    });
    expectForm('put', '/draft', {
      namespaceId: 'public',
      agentName: 'demo',
      version: '1.0.0',
      callInterfaces: '[{}]',
    });

    await agentApi.updateLabels({
      namespaceId: 'public',
      agentName: 'demo',
      labels: '{"stable":"1.0.0"}',
    });
    expectForm('put', '/labels', {
      namespaceId: 'public',
      agentName: 'demo',
      labels: '{"stable":"1.0.0"}',
    });
  });

  it.each([
    ['submit', '/submit'],
    ['publish', '/publish'],
    ['forcePublish', '/force-publish'],
    ['redraft', '/redraft'],
    ['online', '/online'],
    ['offline', '/offline'],
  ] as const)('posts %s to its exact lifecycle route', async (method, path) => {
    await agentApi[method]({
      namespaceId: 'public',
      agentName: 'demo',
      version: '1.0.0',
    });
    expectForm('post', path, {
      namespaceId: 'public',
      agentName: 'demo',
      version: '1.0.0',
    });
  });
});
