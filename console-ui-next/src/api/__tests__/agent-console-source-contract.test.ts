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
const NEXT_UI_DIRECTORIES = [
  path.join(ROOT, 'console-ui-next/src/api'),
  path.join(ROOT, 'console-ui-next/src/pages/agentManagement'),
  path.join(ROOT, 'console-ui-next/src/pages/agentDetail'),
  path.join(ROOT, 'console-ui-next/src/pages/newAgent'),
];
const LEGACY_UI_DIRECTORIES = [
  path.join(ROOT, 'console-ui/src/pages/AI/AgentManagement'),
  path.join(ROOT, 'console-ui/src/pages/AI/AgentDetail'),
  path.join(ROOT, 'console-ui/src/pages/AI/NewAgent'),
];

function sourceFiles(directory: string): string[] {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return entry.name === '__tests__' ? [] : sourceFiles(target);
    }
    return /\.(js|jsx|ts|tsx)$/.test(entry.name) ? [target] : [];
  });
}

describe('Agent Console source contract', () => {
  it('keeps the next Console on generic Agent APIs and legacy Console on A2A compatibility', () => {
    const nextSources = NEXT_UI_DIRECTORIES.flatMap(sourceFiles)
      .map((file) => fs.readFileSync(file, 'utf8'))
      .join('\n');
    const legacySources = LEGACY_UI_DIRECTORIES.flatMap(sourceFiles)
      .map((file) => fs.readFileSync(file, 'utf8'))
      .join('\n');

    expect(nextSources).not.toContain('/v3/console/ai/a2a');
    expect(nextSources).not.toContain('v3/console/ai/a2a');
    expect(fs.readFileSync(
      path.join(ROOT, 'console-ui-next/src/api/agent.ts'),
      'utf8',
    )).toContain("const BASE = 'v3/console/ai/agents'");
    expect(legacySources).toContain('v3/console/ai/a2a');
    expect(legacySources).not.toContain('v3/console/ai/agents');
  });

  it('keeps next Console Runtime reads exact to Agent, Version and protocol', () => {
    const nextApi = fs.readFileSync(
      path.join(ROOT, 'console-ui-next/src/api/agent.ts'),
      'utf8',
    );
    expect(nextApi).toContain('getRuntimeEndpoints');
    expect(nextApi).toContain("client.get(`${BASE}/runtime-endpoints`, { params })");
  });

  it('keeps next Console editable endpoint rows mounted while their URI changes', () => {
    const nextPage = fs.readFileSync(
      path.join(ROOT, 'console-ui-next/src/pages/newAgent/index.tsx'),
      'utf8',
    );

    expect(nextPage).not.toContain('key={`${index}-${endpoint.uri}`}');
    expect(nextPage).toContain('key={index}');
  });
});
