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

import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const ROOT = path.resolve(import.meta.dirname, '../../../..');

function read(relativePath: string): string {
  return fs.readFileSync(path.join(ROOT, relativePath), 'utf8');
}

describe('MCP Console lifecycle source contract', () => {
  it('keeps the next UI mutation path on lifecycle APIs only', () => {
    const api = read('console-ui-next/src/api/mcp.ts');
    const management = read('console-ui-next/src/pages/mcpServerManagement/index.tsx');
    const editor = read('console-ui-next/src/pages/newMcpServer/index.tsx');
    const detail = read('console-ui-next/src/pages/mcpServerDetail/index.tsx');
    const mutationSources = `${management}\n${editor}\n${detail}`;

    expect(api).toContain("client.post(`${BASE}/draft`");
    expect(api).toContain("client.put(`${BASE}/draft`");
    expect(api).toContain("postVersionAction('force-publish', data)");
    expect(api).not.toContain('createMcpServer:');
    expect(api).not.toContain('updateMcpServer:');
    expect(mutationSources).not.toContain('mcpApi.createMcpServer(');
    expect(mutationSources).not.toContain('mcpApi.updateMcpServer(');
    expect(editor).toContain("mode === 'draft-edit'");
    expect(editor).toContain("mode === 'draft-create'");
    expect(management).toContain('handleDetail(name)');
    expect(detail).toContain('<McpClientConfigCard configurations={clientConfigurations} />');
    expect(detail).not.toContain("t('mcp.capabilities')");
    expect(detail).not.toContain("t('mcp.copyConfig')");
  });

  it('leaves the legacy UI on its direct-online create and update contract', () => {
    const legacyEditor = read('console-ui/src/pages/AI/NewMcpServer/NewMcpServer.js');

    expect(legacyEditor).toContain('params[\'latest\'] = isPublish');
    expect(legacyEditor).toContain('return this.createMcpServer(params)');
    expect(legacyEditor).toContain("url: 'v3/console/ai/mcp'");
    expect(legacyEditor).toContain("method: 'post'");
    expect(legacyEditor).toContain("method: 'put'");
    expect(legacyEditor).not.toContain('/draft');
  });
});
