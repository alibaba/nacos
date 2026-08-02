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
// @ts-expect-error The legacy Console is JavaScript and intentionally shares this contract test.
import * as legacy from '../../../../../console-ui/src/pages/AI/agent-console-model.js';

const values = {
  agentName: ' demo ',
  version: ' 1.0.0 ',
  displayName: ' Demo ',
  description: '',
  iconUrl: '',
  providerName: '',
  providerUrl: '',
  tags: 'one, two',
  extensions: '{"region":"cn"}',
  status: 'enable',
  protocolEditorKind: 'raw',
  agentCard: legacy.DEFAULT_AGENT_CARD,
  customProtocol: 'custom',
  customProtocolVersion: '',
  customDescriptorMediaType: 'application/json',
  customNativeDescriptor: '{}',
  endpointSourceMode: 'declared-runtime',
  declaredEndpoints: [],
  callInterfaces: legacy.DEFAULT_CALL_INTERFACES,
  basedOnVersion: ' 0.9.0 ',
  author: '',
  changeDescription: '',
};

describe('Legacy Agent Console editor model', () => {
  it('builds direct and copied drafts with the same generic contract', () => {
    expect(legacy.buildDraftCreateData('public', values, true, 'direct')).toMatchObject({
      namespaceId: 'public',
      agentName: 'demo',
      version: '1.0.0',
      displayName: 'Demo',
      tags: '["one","two"]',
      extensions: '{"region":"cn"}',
    });
    expect(legacy.buildDraftCreateData('public', values, false, 'copy')).toEqual({
      namespaceId: 'public',
      agentName: 'demo',
      version: '1.0.0',
      author: undefined,
      changeDescription: undefined,
      basedOnVersion: '0.9.0',
    });
  });

  it('builds draft and metadata updates and maps metadata to the form', () => {
    expect(legacy.buildDraftUpdateData('public', values)).toMatchObject({
      namespaceId: 'public',
      agentName: 'demo',
      version: '1.0.0',
    });
    expect(legacy.buildMetadataUpdateData('public', values)).toMatchObject({
      namespaceId: 'public',
      agentName: 'demo',
      status: 'enable',
    });
    expect(legacy.metadataToFormValues({
      agentName: 'demo',
      provider: { name: 'Provider', url: 'https://example.com' },
      tags: ['one'],
      extensions: { region: 'cn' },
      status: 'disable',
    })).toMatchObject({
      agentName: 'demo',
      providerName: 'Provider',
      providerUrl: 'https://example.com',
      tags: 'one',
      status: 'disable',
    });
    expect(legacy.metadataToFormValues({ agentName: 'minimal', status: 'enable' }))
      .toMatchObject({ providerName: '', providerUrl: '', tags: '', extensions: '' });
  });

  it('normalizes A2A AgentCard and custom HTTP+JSON endpoint forms', () => {
    const a2a = legacy.buildDraftCreateData('public', {
      ...values,
      protocolEditorKind: 'a2a',
      agentCard: JSON.stringify({
        supportedInterfaces: [{
          url: 'https://agent.example.com/a2a',
          protocolBinding: 'HTTP+JSON',
          protocolVersion: '0.3',
        }],
      }),
    }, true, 'direct');
    const a2aInterface = JSON.parse(a2a.callInterfaces)[0];
    expect(a2aInterface).toMatchObject({
      protocol: 'a2a',
      protocolVersion: '0.3',
      endpointSourceOrder: ['DECLARED', 'RUNTIME'],
      declaredEndpoints: [{
        uri: 'https://agent.example.com/a2a',
        transport: 'HTTP+JSON',
      }],
    });
    expect(a2aInterface.nativeDescriptor).toMatchObject({ name: 'demo', version: '1.0.0' });

    const custom = legacy.buildDraftCreateData('public', {
      ...values,
      protocolEditorKind: 'custom',
      customProtocol: 'json-rpc',
      customNativeDescriptor: '{"method":"invoke"}',
      declaredEndpoints: [{
        uri: 'https://agent.example.com/rpc',
        transport: 'HTTP+JSON',
      }],
    }, true, 'direct');
    expect(JSON.parse(custom.callInterfaces)[0]).toMatchObject({
      protocol: 'json-rpc',
      nativeDescriptor: { method: 'invoke' },
      declaredEndpoints: [{
        uri: 'https://agent.example.com/rpc',
        transport: 'HTTP+JSON',
      }],
    });
  });

  it('projects A2A imports and preserves ordered multi-protocol creation', () => {
    const projection = legacy.projectA2aAgentCard(JSON.stringify({
      name: 'legacy-import',
      version: '1.2.0',
      description: 'Imported Agent',
      provider: { organization: 'Nacos', url: 'https://nacos.io' },
      supportedInterfaces: [{
        url: 'https://agent.example.com/a2a',
        protocolBinding: 'HTTP+JSON',
        protocolVersion: '0.3',
      }],
      skills: [{ tags: ['one', 'two'] }, { tags: ['two', 'three'] }],
    }));
    expect(projection).toMatchObject({
      agentName: 'legacy-import',
      version: '1.2.0',
      providerName: 'Nacos',
      tags: 'one, two, three',
    });

    const custom = {
      ...legacy.createStructuredProtocolEditor('custom'),
      customProtocol: 'json-rpc',
      customNativeDescriptor: '{}',
      declaredEndpoints: [],
    };
    const result = legacy.buildDraftCreateData('public', {
      ...values,
      agentName: projection.agentName,
      version: projection.version,
    }, true, 'direct', [projection.protocolEditor, custom]);
    expect(JSON.parse(result.callInterfaces).map((item: { protocol: string }) => item.protocol))
      .toEqual(['a2a', 'json-rpc']);
    expect(() => legacy.buildDraftCreateData(
      'public', values, true, 'direct', [custom, custom]
    )).toThrow('protocol must be unique: json-rpc');
    expect(() => legacy.buildDraftCreateData('public', values, true, 'direct', []))
      .toThrow('at least one protocol is required');
    expect(() => legacy.projectA2aAgentCard('{}')).toThrow('AgentCard name is required');
  });

  it('keeps legacy import tolerance and custom endpoint defaults aligned with next Console', () => {
    const projection = legacy.projectA2aAgentCard(`{
      "name": "legacy-import",
      "supportedInterfaces": [{
        "url": "https://agent.example.com/a2a",
        "protocolBinding": "HTTP+JSON",
        "protocolVersion": "0.3",
      },],
    }`, '0.0.1');

    expect(projection.version).toBe('0.0.1');
    expect(JSON.parse(projection.protocolEditor.agentCard)).toMatchObject({
      name: 'legacy-import',
      version: '0.0.1',
    });
    expect(legacy.createStructuredProtocolEditor('custom').declaredEndpoints).toEqual([
      { uri: '', transport: 'HTTP' },
    ]);
  });

  it('restores structured and raw editors from exact version content', () => {
    expect(legacy.callInterfacesToFormValues([{
      protocol: 'a2a',
      nativeDescriptor: { name: 'demo' },
      endpointSourceOrder: ['DECLARED', 'RUNTIME'],
    }])).toMatchObject({ protocolEditorKind: 'a2a' });
    expect(legacy.callInterfacesToFormValues([{
      protocol: 'json-rpc',
      protocolVersion: '1',
      descriptorMediaType: 'application/json',
      nativeDescriptor: {},
      endpointSourceOrder: ['RUNTIME'],
      declaredEndpoints: [{ uri: 'https://example.com', transport: 'HTTP+JSON' }],
    }])).toMatchObject({
      protocolEditorKind: 'custom',
      endpointSourceMode: 'runtime-only',
      customProtocol: 'json-rpc',
    });
    expect(legacy.callInterfacesToFormValues([])).toEqual({
      protocolEditorKind: 'raw',
      callInterfaces: '[]',
    });
  });

  it('covers lifecycle, protocol, Runtime cache and Naming navigation helpers', () => {
    expect(legacy.getVersionActions('draft')).toHaveLength(4);
    expect(legacy.getVersionActions('reviewing')).toEqual(['forcePublish']);
    expect(legacy.getVersionActions('reviewed')).toEqual(['publish', 'forcePublish', 'redraft']);
    expect(legacy.getVersionActions('online')).toEqual(['offline']);
    expect(legacy.getVersionActions('offline')).toEqual(['online']);
    expect(legacy.getVersionActions('unknown')).toEqual([]);
    expect(legacy.getProtocols(null)).toEqual([]);
    expect(legacy.getProtocols([
      { protocol: 'A2A' },
      { protocol: 'A2A' },
      { protocol: 'MCP' },
    ])).toEqual(['A2A', 'MCP']);
    expect(legacy.usesRuntimeSource(undefined)).toBe(false);
    expect(legacy.usesRuntimeSource({ endpointSourceOrder: undefined })).toBe(false);
    expect(legacy.usesRuntimeSource({ endpointSourceOrder: ['RUNTIME'] })).toBe(true);
    expect(legacy.runtimeCacheKey('1.0.0', 'A2A')).toBe('1.0.0@@A2A');
    expect(legacy.namingDetailPath({
      namespaceId: 'tenant a',
      groupName: 'AI_GROUP',
      serviceName: 'service/name',
    })).toBe('/serviceDetail?namespaceId=tenant+a&groupName=AI_GROUP&name=service%2Fname');
  });

  it.each([
    [{ ...values, agentName: '' }, true, 'direct', 'agentName is required'],
    [{ ...values, version: '' }, true, 'direct', 'version is required'],
    [{ ...values, callInterfaces: '{' }, true, 'direct', 'callInterfaces must be valid JSON'],
    [{ ...values, callInterfaces: '[]' }, true, 'direct', 'callInterfaces must be a non-empty JSON array'],
    [{ ...values, basedOnVersion: '' }, false, 'copy', 'basedOnVersion is required'],
    [{ ...values, providerUrl: 'https://example.com' }, true, 'direct', 'providerName is required when providerUrl is set'],
    [{ ...values, extensions: '[]' }, true, 'direct', 'extensions must be a JSON object'],
    [{ ...values, extensions: '{' }, true, 'direct', 'extensions must be valid JSON'],
  ])('rejects invalid legacy editor combinations %#', (formValues, initial, mode, message) => {
    expect(() => legacy.buildDraftCreateData('public', formValues, initial, mode))
      .toThrow(message);
  });
});
