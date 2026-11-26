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
vi.mock('../client', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
import client from '../client';
import { hubResourceApi, sourceResourcePath, sharedResourceSummary, hubResourceConfig, type HubResourceType } from '../hub-resource';

beforeEach(() => vi.resetAllMocks());
const types: HubResourceType[] = ['mcp', 'agent', 'prompt', 'agentspec'];
function row(type: HubResourceType) {
  return { namespaceId: '__nacos_ai_hub__', [hubResourceConfig[type].responseIdentity]: '501',
    hub: { hubEntryId: '501', resourceType: type, resourceName: 'same-name', version: '1.0.0',
      updateTime: 1700000000000, sourceDisplayName: 'public' } };
}
describe('shared directory modification timestamps', () => {
  it.each(types)('%s uses the Hub entry timestamp before source-shaped top-level metadata', type => {
    expect(sharedResourceSummary(type, { ...row(type), updateTime: 1800000000000,
      gmtModified: 1900000000000 }).updateTime).toBe(1700000000000);
  });
  it('MCP list maps the timestamp even when BasicInfo has no top-level date field', async () => {
    vi.mocked(client.get).mockResolvedValue({ code: 0, data: { totalCount: 1, pageItems: [row('mcp')] } });
    const page = await hubResourceApi.list('mcp', { name: '', pageNo: 1, pageSize: 12 });
    expect(page.pageItems[0].updateTime).toBe(1700000000000);
    expect(page.pageItems[0].resourceName).toBe('same-name');
    expect(client.get).toHaveBeenCalledWith('v3/console/ai/mcp/list', { params: {
      namespaceId: '__nacos_ai_hub__', mcpName: '', search: 'blur', pageNo: 1, pageSize: 12, orderBy: undefined,
    } });
  });
  it('an absent timestamp stays absent rather than pretending to be the current time', () => {
    const value = row('mcp');
    Reflect.deleteProperty(value.hub, 'updateTime');
    expect(sharedResourceSummary('mcp', value).updateTime).toBeUndefined();
  });
});

describe('shared detail presentation preserves resource contracts', () => {
  it('keeps the empty AgentSpec manifest from the pinned version request unchanged', async () => {
    const meta = row('agentspec');
    const body = { ...meta, version: '1.0.0', content: '', resource: {} };
    vi.mocked(client.get).mockResolvedValueOnce({ code: 0, data: meta }).mockResolvedValueOnce({ code: 0, data: body });
    const detail = await hubResourceApi.detail('agentspec', '501');
    expect(await hubResourceApi.content('agentspec', detail.hubEntryId, detail.version)).toEqual(body);
    expect(client.get).toHaveBeenLastCalledWith('v3/console/ai/agentspecs/version', { params: { namespaceId: '__nacos_ai_hub__', agentSpecName: '501', version: '1.0.0' } });
  });
  it('retains resolved MCP endpoints for installation configuration without switching namespaces', async () => {
    const body = { ...row('mcp'), protocol: 'mcp-sse', backendEndpoints: [{ protocol: 'https', address: 'mcp.example', port: '443', path: '/sse' }], bizTags: '["demo"]' };
    vi.mocked(client.get).mockResolvedValueOnce({ code: 0, data: body });
    const detail = await hubResourceApi.detail('mcp', '501');
    expect(detail.mcp?.name).toBe('same-name');
    expect(detail.mcp?.backendEndpoints).toEqual(body.backendEndpoints);
    expect(detail.tags).toEqual(['demo']);
    expect(detail.downloadCount).toBe(0);
  });
});

describe('Prompt Hub reuses the ordinary Markdown download contract', () => {
  it('pins the shared entry and version and returns server Markdown unchanged', async () => {
    const blob = new Blob(['# Prompt\nHello {{name}}'], { type: 'text/markdown;charset=utf-8' });
    vi.mocked(client.get).mockResolvedValueOnce(blob);
    expect(await hubResourceApi.downloadPrompt('501', '1.0.0')).toBe(blob);
    expect(client.get).toHaveBeenCalledWith('v3/console/ai/prompt/version/download', { params: { namespaceId: '__nacos_ai_hub__', promptKey: '501', version: '1.0.0' }, responseType: 'blob' });
  });
  it('does not save an error document as a Prompt file', async () => {
    vi.mocked(client.get).mockResolvedValueOnce(new Blob(['{"code":403}'], { type: 'application/json' }));
    await expect(hubResourceApi.downloadPrompt('501', '1.0.0')).rejects.toThrow('Invalid Prompt download response');
    await expect(hubResourceApi.downloadPrompt('501', '')).rejects.toThrow('Missing pinned resource version');
  });
});

describe('resource Hub import', () => {
  it.each(types)('%s imports only the Hub identity into an independent draft', async type => {
    const request = { hubEntryId: '501', targetNamespaceId: 'target', targetName: 'imported', targetVersion: '0.0.1' };
    const created = { resourceType: type, hubEntryId: '501', targetNamespaceId: 'target', targetName: 'imported',
      draftVersion: '0.0.1', targetResourceId: '601', status: 'draft', origin: 'HUB' };
    vi.mocked(client.post).mockResolvedValueOnce({ code: 0, data: created });
    expect(await hubResourceApi.import(type, request)).toEqual(created);
    const [path, payload] = vi.mocked(client.post).mock.calls[0];
    expect(path).toBe(`v3/console/ai/hub/${type}/import`);
    expect((payload as URLSearchParams).get('hubEntryId')).toBe('501');
    expect((payload as URLSearchParams).has('sourceNamespaceId')).toBe(false);
    vi.mocked(client.post).mockResolvedValueOnce({ code: 0, data: { ...created, targetName: 'other' } });
    await expect(hubResourceApi.import(type, request)).rejects.toThrow('Mismatched imported');
  });
});

describe('independently authorized fixed-source navigation', () => {
  it.each(types)('%s requires sourceReadable and pins the original namespace/name/version', type => {
    const raw = row(type);
    const source = { sourceReadable: true, sourceNamespaceId: 'team-a', sourceName: 'original/name' };
    const allowed = sharedResourceSummary(type, { ...raw, hub: { ...raw.hub, ...source } });
    const path = sourceResourcePath(allowed)!;
    const query = new URLSearchParams(path.split('?')[1]);
    expect(query.get('namespaceId')).toBe('team-a');
    expect(query.get('version')).toBe('1.0.0');
    if (type === 'agentspec') expect(path.split('?')[0]).toBe('/agentspec/original%2Fname');
    else expect(query.get(type === 'agent' ? 'name' : hubResourceConfig[type].identity)).toBe('original/name');
    const denied = sharedResourceSummary(type, { ...raw, hub: { ...raw.hub, ...source, sourceReadable: false } });
    expect(denied.sourceNamespaceId).toBeUndefined();
    expect(denied.sourceName).toBeUndefined();
    expect(sourceResourcePath(denied)).toBeNull();
    expect(sourceResourcePath({ ...allowed, sourceNamespaceId: '__nacos_ai_hub__' })).toBeNull();
  });
});
