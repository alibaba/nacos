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

const BASE = 'v3/console/ai/mcp';
const mockClient = {
  get: vi.fn().mockResolvedValue({ data: {} }),
  post: vi.fn().mockResolvedValue({ data: {} }),
  put: vi.fn().mockResolvedValue({ data: {} }),
  delete: vi.fn().mockResolvedValue({ data: {} }),
};

vi.mock('../client', () => ({ default: mockClient }));

const { mcpApi, toMcpFormParams } = await import('../mcp');

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

describe('MCP Console lifecycle API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('encodes only present form fields', () => {
    expect(Object.fromEntries(toMcpFormParams({
      omitted: undefined,
      absent: null,
      empty: '',
      enabled: false,
    }).entries())).toEqual({ empty: '', enabled: 'false' });
  });

  it('uses exact lifecycle read and draft delete routes', async () => {
    const identity = { namespaceId: 'public', mcpName: 'demo', version: '1.0.0' };
    const list = {
      namespaceId: 'public',
      mcpName: 'demo',
      status: 'online' as const,
      pageNo: 1,
      pageSize: 20,
    };
    await mcpApi.listVersions(list);
    expect(mockClient.get).toHaveBeenLastCalledWith(`${BASE}/versions`, { params: list });
    await mcpApi.getVersion(identity);
    expect(mockClient.get).toHaveBeenLastCalledWith(`${BASE}/version`, { params: identity });
    await mcpApi.deleteDraft(identity);
    expect(mockClient.delete).toHaveBeenLastCalledWith(`${BASE}/draft`, { params: identity });
  });

  it('uses form-encoded draft and label routes', async () => {
    const draft = {
      namespaceId: 'public',
      mcpName: 'demo',
      version: '1.0.0',
      serverSpecification: '{"name":"demo"}',
      toolSpecification: '{}',
    };
    await mcpApi.createDraft(draft);
    expectForm('post', '/draft', draft);
    await mcpApi.updateDraft(draft);
    expectForm('put', '/draft', draft);
    await mcpApi.updateLabels({
      namespaceId: 'public',
      mcpName: 'demo',
      labels: '{"stable":"1.0.0"}',
    });
    expectForm('put', '/labels', {
      namespaceId: 'public',
      mcpName: 'demo',
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
    await mcpApi[method]({ namespaceId: 'public', mcpName: 'demo', version: '1.0.0' });
    expectForm('post', path, {
      namespaceId: 'public',
      mcpName: 'demo',
      version: '1.0.0',
    });
  });
});
