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

import { describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
vi.mock('../client', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
import { ordinaryResourcePath } from '../hub-resource';

describe('destination detail after Hub import or cross-namespace copy', () => {
  it('opens the created Agent draft using the existing name route parameter', () => {
    const url = new URL(ordinaryResourcePath('agent', 'public', '321-8', '0.0.1'), 'https://console.example');
    const source = readFileSync(resolve(__dirname, '../../pages/agentDetail/index.tsx'), 'utf8');
    // Read the actual destination page contract rather than the HTTP API parameter map.
    const routeName = source.match(/const agentName = searchParams.get\('([^']+)'\)/)?.[1];
    expect(routeName).toBe('name');
    expect(url.pathname).toBe('/agentDetail');
    expect(url.searchParams.get(routeName!)).toBe('321-8');
    expect(url.searchParams.get('namespaceId')).toBe('public');
    expect(url.searchParams.get('version')).toBe('0.0.1');
    expect(url.searchParams.has('agentName')).toBe(false);
  });
  it.each([
    ['mcp', '/mcpServerDetail', 'mcpName'],
    ['prompt', '/promptDetail', 'promptKey'],
  ] as const)('%s preserves its existing detail route and destination identity', (type, path, parameter) => {
    const url = new URL(ordinaryResourcePath(type, 'target-ns', 'renamed', '0.2.0'), 'https://console.example');
    expect(url.pathname).toBe(path);
    expect(url.searchParams.get(parameter)).toBe('renamed');
    expect(url.searchParams.get('namespaceId')).toBe('target-ns');
    expect(url.searchParams.get('version')).toBe('0.2.0');
  });
  it('keeps AgentSpec identity in the encoded path and encodes Agent target names safely', () => {
    const name = 'new + & 测试';
    const agent = new URL(ordinaryResourcePath('agent', 'target & namespace', name, '0.0.2'), 'https://console.example');
    expect(agent.searchParams.get('name')).toBe(name);
    expect(agent.searchParams.get('namespaceId')).toBe('target & namespace');
    const spec = new URL(ordinaryResourcePath('agentspec', 'target-ns', name, '0.0.2'), 'https://console.example');
    expect(decodeURIComponent(spec.pathname)).toBe(`/agentspec/${name}`);
    expect(spec.searchParams.get('version')).toBe('0.0.2');
  });
});
