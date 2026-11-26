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

import { describe, expect, it } from 'vitest';
import JSZip from 'jszip';
import { agentSpecContent } from '../hub-resource-content';
import { agentSpecArchive, resourceDefinition } from '../hub-resource-downloads';
import type { SharedResource } from '@/api/hub-resource';

const detail: SharedResource = { resourceType: 'agentspec', hubEntryId: '501', resourceName: 'same', version: '0.0.2' };
describe('AgentSpec Hub raw manifest compatibility', () => {
  it.each(['', '  ', '{', '{"worker":', '{"worker":{"suggested_name":"same"}}'])('preserves manifest %j without parsing or fabricating content', async manifest => {
    const body = { content: manifest, resource: {} };
    expect(agentSpecContent(body).manifest).toBe(manifest);
    expect(resourceDefinition(detail, body)).toEqual({ manifest, resources: {} });
    const blob = await agentSpecArchive(body);
    const zip = await JSZip.loadAsync(await blob.arrayBuffer());
    expect(await zip.file('manifest.json')!.async('string')).toBe(manifest);
  });
  it('reads AGENTS.md by basename like the ordinary page and retains empty/binary files', async () => {
    const body = { content: '', resource: {
      main: { type: 'text', name: 'docs/AGENTS.md', content: '# Instructions', metadata: null },
      empty: { type: 'text', name: 'empty.txt', content: '', metadata: null },
      image: { type: 'binary', name: 'image.bin', content: 'AAH/', metadata: { encoding: 'base64' } },
    } };
    const view = agentSpecContent(body);
    expect(view.agents).toBe('# Instructions');
    expect(Object.keys(view.resources)).toEqual(['empty', 'image']);
    expect(body.resource.main.content).toBe('# Instructions');
    const zip = await JSZip.loadAsync(await (await agentSpecArchive(body)).arrayBuffer());
    expect(await zip.file('empty.txt')!.async('string')).toBe('');
    expect([...await zip.file('image.bin')!.async('uint8array')]).toEqual([0, 1, 255]);
  });
  it('handles an absent legacy manifest for viewing, without inventing bytes for download', async () => {
    expect(agentSpecContent({}).manifest).toBe('');
    await expect(agentSpecArchive({})).rejects.toThrow('Missing AgentSpec manifest');
  });
  it('still rejects malformed resource payloads and unsafe archive destinations', async () => {
    expect(() => agentSpecContent({ content: 42 })).toThrow('Invalid AgentSpec manifest');
    expect(() => agentSpecContent({ resource: [] })).toThrow('Invalid AgentSpec resource map');
    expect(() => agentSpecContent({ resource: { bad: {} } })).toThrow('Invalid AgentSpec resource');
    await expect(agentSpecArchive({ content: '{}', resource: { bad: { name: '../escape', type: 'text', content: '' } } })).rejects.toThrow('Unsafe AgentSpec archive path');
  });
});
