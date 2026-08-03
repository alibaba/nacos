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
import type { AgentCallInterface } from '@/types/agent';
import type { AgentEditorValues, EndpointSourceMode } from '../agent-console-model';
import {
  buildDraftCreateData,
  buildDraftUpdateData,
  buildMetadataUpdateData,
  callInterfacesToEditorValues,
  callInterfacesToText,
  createStructuredProtocolEditor,
  getProtocols,
  getVersionActions,
  metadataToEditorValues,
  namingDetailPath,
  projectA2aAgentCard,
  runtimeCacheKey,
  usesRuntimeSource,
} from '../agent-console-model';

const CALL_INTERFACES: AgentCallInterface[] = [
  {
    protocol: 'custom',
    protocolVersion: '1.0.0',
    descriptorMediaType: 'application/json',
    nativeDescriptor: { name: 'demo' },
    endpointSourceOrder: ['RUNTIME', 'DECLARED'],
    declaredEndpoints: [],
  },
];

function values(overrides: Partial<AgentEditorValues> = {}): AgentEditorValues {
  return {
    agentName: ' demo-agent ',
    version: ' 1.0.0 ',
    displayName: ' Demo ',
    description: ' Description ',
    iconUrl: ' https://example.com/icon.png ',
    providerName: ' Provider ',
    providerUrl: ' https://example.com ',
    tags: 'alpha, beta, alpha, ',
    extensions: '{"region":"cn-hangzhou"}',
    status: 'enable',
    protocolEditorKind: 'raw',
    agentCard: '{}',
    customProtocol: 'custom',
    customProtocolVersion: '1.0.0',
    customDescriptorMediaType: 'application/json',
    customNativeDescriptor: '{"name":"demo"}',
    endpointSourceMode: 'runtime-declared',
    declaredEndpoints: [],
    callInterfaces: JSON.stringify(CALL_INTERFACES),
    basedOnVersion: ' 0.9.0 ',
    author: ' author ',
    changeDescription: ' first draft ',
    ...overrides,
  };
}

function parseInterface(result: { callInterfaces?: string }): AgentCallInterface {
  return JSON.parse(result.callInterfaces || '[]')[0];
}

describe('Agent Console editor model', () => {
  it('builds the complete initial draft with raw direct content', () => {
    expect(buildDraftCreateData('public', values(), true, 'direct')).toEqual({
      namespaceId: 'public',
      agentName: 'demo-agent',
      version: '1.0.0',
      author: 'author',
      changeDescription: 'first draft',
      callInterfaces: JSON.stringify(CALL_INTERFACES),
      displayName: 'Demo',
      description: 'Description',
      iconUrl: 'https://example.com/icon.png',
      provider: '{"name":"Provider","url":"https://example.com"}',
      tags: '["alpha","beta","alpha"]',
      extensions: '{"region":"cn-hangzhou"}',
    });
  });

  it('normalizes an A2A 1.0 Agent Card and derives HTTP+JSON endpoints', () => {
    const result = buildDraftCreateData('public', values({
      protocolEditorKind: 'a2a',
      agentCard: JSON.stringify({
        name: 'ignored-name',
        version: 'ignored-version',
        description: 'Research assistant',
        supportedInterfaces: [
          {
            url: 'https://agent.example.com/a2a',
            protocolBinding: 'HTTP+JSON',
            protocolVersion: '0.3',
            customField: true,
          },
          {
            url: 'https://agent.example.com:443/duplicate',
            transport: 'HTTP+JSON',
            protocolVersion: '0.3',
          },
          {
            url: 'ws://stream.example.com/a2a',
            protocolBinding: 'WebSocket',
            protocolVersion: '0.3',
          },
        ],
        capabilities: { streaming: true },
        defaultInputModes: ['text/plain'],
        defaultOutputModes: ['text/plain'],
        skills: [],
        extensionField: 'preserved',
      }),
    }), true, 'direct');
    const callInterface = parseInterface(result);

    expect(callInterface.protocol).toBe('a2a');
    expect(callInterface.protocolVersion).toBe('0.3');
    expect(callInterface.descriptorMediaType).toBe('application/json');
    expect(callInterface.endpointSourceOrder).toEqual(['DECLARED', 'RUNTIME']);
    expect(callInterface.declaredEndpoints).toEqual([
      { uri: 'https://agent.example.com/a2a', transport: 'HTTP+JSON' },
      { uri: 'ws://stream.example.com/a2a', transport: 'WebSocket' },
    ]);
    const card = callInterface.nativeDescriptor as Record<string, unknown>;
    expect(card.name).toBe('demo-agent');
    expect(card.version).toBe('1.0.0');
    expect(card.extensionField).toBe('preserved');
    expect(card.url).toBe('https://agent.example.com/a2a');
    expect(card.preferredTransport).toBe('HTTP+JSON');
    expect(card.protocolVersion).toBe('0.3');
    expect((card.supportedInterfaces as Array<Record<string, unknown>>)[0]).toMatchObject({
      protocolBinding: 'HTTP+JSON',
      transport: 'HTTP+JSON',
      customField: true,
    });
  });

  it('normalizes legacy A2A root and additional interfaces without dropping fields', () => {
    const callInterface = parseInterface(buildDraftCreateData('public', values({
      protocolEditorKind: 'a2a',
      agentCard: JSON.stringify({
        url: 'http://legacy.example.com/a2a',
        preferredTransport: 'HTTP+JSON',
        protocolVersion: '0.2',
        additionalInterfaces: [
          { url: 'wss://stream.example.com/a2a' },
          { url: 'https://api.example.com:8443/a2a', protocolBinding: 'HTTP+JSON' },
        ],
      }),
    }), true, 'direct'));

    expect(callInterface.declaredEndpoints).toEqual([
      { uri: 'http://legacy.example.com/a2a', transport: 'HTTP+JSON' },
      { uri: 'wss://stream.example.com/a2a', transport: 'HTTP+JSON' },
      { uri: 'https://api.example.com:8443/a2a', transport: 'HTTP+JSON' },
    ]);
    expect(callInterface.nativeDescriptor).toMatchObject({
      name: 'demo-agent',
      version: '1.0.0',
      description: '',
      capabilities: {},
      defaultInputModes: [],
      defaultOutputModes: [],
      skills: [],
    });
  });

  it('projects one complete A2A AgentCard into an import-only initial draft', () => {
    const projection = projectA2aAgentCard(JSON.stringify({
      name: 'Research Assistant Agent',
      version: '0.3.0',
      description: 'Research with citations',
      provider: {
        organization: 'Nacos Labs',
        url: 'https://nacos.io',
      },
      supportedInterfaces: [{
        url: 'https://agent.example.com/a2a',
        protocolBinding: 'HTTP+JSON',
        protocolVersion: '0.3',
      }],
      skills: [
        { tags: ['research', 'citations'] },
        { tags: ['citations', 'academic'] },
      ],
    }));

    expect(projection).toMatchObject({
      agentName: 'Research Assistant Agent',
      version: '0.3.0',
      displayName: 'Research Assistant Agent',
      description: 'Research with citations',
      providerName: 'Nacos Labs',
      providerUrl: 'https://nacos.io',
      tags: 'research, citations, academic',
      protocolEditor: { protocolEditorKind: 'a2a' },
    });
    const importedValues = values({
      ...projection,
      agentCard: projection.protocolEditor.agentCard,
      protocolEditorKind: 'a2a',
    });
    const result = buildDraftCreateData('public', importedValues, true, 'direct');
    expect(result).toMatchObject({
      agentName: 'Research Assistant Agent',
      version: '0.3.0',
      displayName: 'Research Assistant Agent',
      description: 'Research with citations',
      provider: '{"name":"Nacos Labs","url":"https://nacos.io"}',
      tags: '["research","citations","academic"]',
    });
  });

  it('accepts trailing commas in A2A imports and uses the visible fallback version', () => {
    const projection = projectA2aAgentCard(`{
      "name": "Research Assistant Agent",
      "description": "Keeps comma-like text,}",
      "supportedInterfaces": [{
        "url": "https://research-agent.example.com/a2a/v1",
        "protocolBinding": "HTTP+JSON",
        "protocolVersion": "0.3",
      },],
    }`, '0.0.1');
    const card = JSON.parse(projection.protocolEditor.agentCard) as Record<string, unknown>;

    expect(projection.version).toBe('0.0.1');
    expect(card).toMatchObject({
      name: 'Research Assistant Agent',
      version: '0.0.1',
      description: 'Keeps comma-like text,}',
    });
    expect(projection.protocolEditor.declaredEndpoints).toEqual([
      { uri: '', transport: 'HTTP' },
    ]);
  });

  it('prefers an explicit AgentCard version and defaults custom endpoints to HTTP', () => {
    const projection = projectA2aAgentCard(JSON.stringify({
      name: 'versioned-agent',
      version: '2.1.0',
      supportedInterfaces: [{
        url: 'https://agent.example.com/a2a',
        protocolBinding: 'HTTP+JSON',
        protocolVersion: '1.0',
      }],
    }), '0.0.1');

    expect(projection.version).toBe('2.1.0');
    expect(createStructuredProtocolEditor('custom').declaredEndpoints).toEqual([
      { uri: '', transport: 'HTTP' },
    ]);
  });

  it('builds ordered unique structured protocols for a new Agent', () => {
    const a2a = createStructuredProtocolEditor('a2a', JSON.stringify({
      name: 'demo-agent',
      version: '1.0.0',
      supportedInterfaces: [{
        url: 'https://agent.example.com/a2a',
        protocolBinding: 'HTTP+JSON',
        protocolVersion: '0.3',
      }],
    }));
    const custom = {
      ...createStructuredProtocolEditor('custom'),
      customProtocol: 'json-rpc',
      customProtocolVersion: '2.0',
      customNativeDescriptor: '{"method":"invoke"}',
      endpointSourceMode: 'runtime-only' as const,
      declaredEndpoints: [],
    };
    const result = buildDraftCreateData('public', values(), true, 'direct', [a2a, custom]);
    const callInterfaces = JSON.parse(result.callInterfaces || '[]') as AgentCallInterface[];

    expect(callInterfaces.map((item) => item.protocol)).toEqual(['a2a', 'json-rpc']);
    expect(callInterfaces[1]).toMatchObject({
      protocolVersion: '2.0',
      endpointSourceOrder: ['RUNTIME'],
      nativeDescriptor: { method: 'invoke' },
    });
  });

  it('rejects empty and duplicate structured protocol collections', () => {
    expect(() => buildDraftCreateData('public', values(), true, 'direct', []))
      .toThrow('at least one protocol is required');
    const first = {
      ...createStructuredProtocolEditor('custom'),
      customProtocol: 'json-rpc',
      customNativeDescriptor: '{}',
      declaredEndpoints: [],
    };
    expect(() => buildDraftCreateData('public', values(), true, 'direct', [first, first]))
      .toThrow('protocol must be unique: json-rpc');
  });

  it.each([
    ['', 'agentCard is required'],
    ['{}', 'AgentCard name is required'],
    ['{"name":"demo"}', 'AgentCard version is required'],
  ])('rejects incomplete one-step A2A import %#', (agentCard, message) => {
    expect(() => projectA2aAgentCard(agentCard)).toThrow(message);
  });

  it.each([
    ['declared-runtime', ['DECLARED', 'RUNTIME']],
    ['runtime-declared', ['RUNTIME', 'DECLARED']],
    ['declared-only', ['DECLARED']],
    ['runtime-only', ['RUNTIME']],
  ] as Array<[EndpointSourceMode, string[]]>) (
    'builds custom protocol source mode %s',
    (endpointSourceMode, expected) => {
      const callInterface = parseInterface(buildDraftCreateData('public', values({
        protocolEditorKind: 'custom',
        customProtocol: 'json-rpc',
        customProtocolVersion: ' ',
        customDescriptorMediaType: 'application/json',
        customNativeDescriptor: '["native", "descriptor"]',
        endpointSourceMode,
        declaredEndpoints: [
          { uri: '', transport: '' },
          { uri: 'https://api.example.com/rpc', transport: 'HTTP+JSON' },
          { uri: 'https://api.example.com:443/duplicate', transport: 'HTTP+JSON' },
        ],
      }), true, 'direct'));
      expect(callInterface).toEqual({
        protocol: 'json-rpc',
        descriptorMediaType: 'application/json',
        nativeDescriptor: ['native', 'descriptor'],
        endpointSourceOrder: expected,
        declaredEndpoints: [
          { uri: 'https://api.example.com/rpc', transport: 'HTTP+JSON' },
        ],
      });
    },
  );

  it('omits empty custom endpoints and keeps an explicit protocol version', () => {
    const callInterface = parseInterface(buildDraftCreateData('public', values({
      protocolEditorKind: 'custom',
      customProtocolVersion: ' 2.0 ',
      customNativeDescriptor: '"opaque"',
      declaredEndpoints: [],
    }), true, 'direct'));
    expect(callInterface.protocolVersion).toBe('2.0');
    expect(callInterface.nativeDescriptor).toBe('opaque');
    expect(callInterface.declaredEndpoints).toBeUndefined();
  });

  it('builds a subsequent draft by copying one exact version without metadata', () => {
    expect(buildDraftCreateData('tenant', values(), false, 'copy')).toEqual({
      namespaceId: 'tenant',
      agentName: 'demo-agent',
      version: '1.0.0',
      author: 'author',
      changeDescription: 'first draft',
      basedOnVersion: '0.9.0',
    });
  });

  it('omits optional values and supports a provider without URL', () => {
    const result = buildDraftCreateData('public', values({
      displayName: ' ',
      description: '',
      iconUrl: '',
      providerName: 'Provider',
      providerUrl: '',
      tags: '',
      extensions: '',
      author: '',
      changeDescription: '',
    }), true, 'direct');

    expect(result.displayName).toBeUndefined();
    expect(result.description).toBeUndefined();
    expect(result.iconUrl).toBeUndefined();
    expect(result.provider).toBe('{"name":"Provider"}');
    expect(result.tags).toBeUndefined();
    expect(result.extensions).toBeUndefined();
    expect(result.author).toBeUndefined();
    expect(result.changeDescription).toBeUndefined();
  });

  it('builds exact draft and metadata replacement forms', () => {
    expect(buildDraftUpdateData('public', values())).toEqual({
      namespaceId: 'public',
      agentName: 'demo-agent',
      version: '1.0.0',
      callInterfaces: JSON.stringify(CALL_INTERFACES),
      changeDescription: 'first draft',
    });
    expect(buildMetadataUpdateData('public', values())).toEqual({
      namespaceId: 'public',
      agentName: 'demo-agent',
      displayName: 'Demo',
      description: 'Description',
      iconUrl: 'https://example.com/icon.png',
      provider: '{"name":"Provider","url":"https://example.com"}',
      tags: '["alpha","beta","alpha"]',
      extensions: '{"region":"cn-hangzhou"}',
      status: 'enable',
    });
  });

  it.each([
    [values({ agentName: ' ' }), true, 'direct', 'agentName is required'],
    [values({ version: '' }), true, 'direct', 'version is required'],
    [values({ callInterfaces: '' }), true, 'direct', 'callInterfaces is required'],
    [values({ callInterfaces: '{' }), true, 'direct', 'callInterfaces must be valid JSON'],
    [values({ callInterfaces: '{}' }), true, 'direct', 'callInterfaces must be a non-empty JSON array'],
    [values({ callInterfaces: '[]' }), true, 'direct', 'callInterfaces must be a non-empty JSON array'],
    [values({ basedOnVersion: '' }), false, 'copy', 'basedOnVersion is required'],
    [values({ providerName: '', providerUrl: 'https://example.com' }), true, 'direct', 'providerName is required when providerUrl is set'],
    [values({ extensions: '{' }), true, 'direct', 'extensions must be valid JSON'],
    [values({ extensions: '[]' }), true, 'direct', 'extensions must be a JSON object'],
    [values({ extensions: 'null' }), true, 'direct', 'extensions must be a JSON object'],
  ] as const)(
    'rejects invalid common editor combinations %#',
    (formValues, initial, mode, message) => {
      expect(() => buildDraftCreateData('public', formValues, initial, mode)).toThrow(message);
    },
  );

  it.each([
    ['', 'agentCard is required'],
    ['{', 'agentCard must be valid JSON'],
    ['[]', 'agentCard must be a JSON object'],
    ['{"supportedInterfaces":[null]}', 'AgentCard interface must be a JSON object'],
    ['{"supportedInterfaces":[{}]}', 'AgentCard interface url is required'],
    ['{"supportedInterfaces":[{"url":"not-a-url","protocolBinding":"HTTP+JSON","protocolVersion":"1"}]}', 'Invalid Endpoint URI'],
    ['{"supportedInterfaces":[{"url":"https://user:secret@example.com/a2a","protocolBinding":"HTTP+JSON","protocolVersion":"1"}]}', 'Invalid Endpoint URI'],
    ['{"supportedInterfaces":[{"url":"https://example.com/a2a#fragment","protocolBinding":"HTTP+JSON","protocolVersion":"1"}]}', 'Invalid Endpoint URI'],
    ['{"supportedInterfaces":[{"url":"https://example.com/a2a","protocolBinding":"HTTP JSON","protocolVersion":"1"}]}', 'transport must contain 1 to 64 letters, digits, +, or -'],
    ['{"supportedInterfaces":[{"url":"https://example.com/a2a","protocolBinding":"HTTP+JSON"}]}', 'AgentCard interface protocolVersion is required'],
    ['{"url":"ftp://example.com/a2a","preferredTransport":"CUSTOM","protocolVersion":"1"}', 'Invalid Endpoint URI'],
  ])('rejects invalid A2A Agent Card %#', (agentCard, message) => {
    expect(() => buildDraftCreateData('public', values({
      protocolEditorKind: 'a2a',
      agentCard,
    }), true, 'direct')).toThrow(message);
  });

  it.each([
    [values({ protocolEditorKind: 'custom', customProtocol: '' }), 'protocol is required'],
    [values({ protocolEditorKind: 'custom', customDescriptorMediaType: '' }), 'descriptorMediaType is required'],
    [values({ protocolEditorKind: 'custom', customNativeDescriptor: '' }), 'nativeDescriptor is required'],
    [values({ protocolEditorKind: 'custom', customNativeDescriptor: '{' }), 'nativeDescriptor must be valid JSON'],
    [values({ protocolEditorKind: 'custom', customNativeDescriptor: 'null' }), 'nativeDescriptor must not be JSON null'],
    [values({ protocolEditorKind: 'custom', declaredEndpoints: [{ uri: '', transport: 'HTTP+JSON' }] }), 'Endpoint uri is required'],
    [values({ protocolEditorKind: 'custom', declaredEndpoints: [{ uri: 'https://example.com', transport: '' }] }), 'transport is required'],
    [values({ protocolEditorKind: 'custom', declaredEndpoints: [{ uri: 'https://example.com', transport: 'HTTP_JSON' }] }), 'transport must contain 1 to 64 letters, digits, +, or -'],
    [values({ protocolEditorKind: 'custom', declaredEndpoints: [{ uri: 'mailto:test@example.com', transport: 'MAIL' }] }), 'Invalid Endpoint URI'],
  ])('rejects invalid custom interface %#', (formValues, message) => {
    expect(() => buildDraftCreateData('public', formValues, true, 'direct')).toThrow(message);
  });

  it('rejects invalid exact draft and metadata inputs', () => {
    expect(() => buildDraftUpdateData('public', values({ agentName: '' })))
      .toThrow('agentName is required');
    expect(() => buildDraftUpdateData('public', values({ version: '' })))
      .toThrow('version is required');
    expect(() => buildMetadataUpdateData('public', values({ agentName: '' })))
      .toThrow('agentName is required');
    expect(() => buildMetadataUpdateData('public', values({
      providerName: '',
      providerUrl: 'https://example.com',
    }))).toThrow('providerName is required when providerUrl is set');
  });

  it('maps metadata back to an editor without inventing values', () => {
    expect(metadataToEditorValues({
      namespaceId: 'public',
      agentName: 'demo-agent',
      displayName: 'Demo',
      provider: { name: 'Provider', url: 'https://example.com' },
      tags: ['alpha', 'beta'],
      extensions: { region: 'cn-hangzhou' },
      status: 'disable',
    })).toEqual({
      agentName: 'demo-agent',
      version: '',
      displayName: 'Demo',
      description: '',
      iconUrl: '',
      providerName: 'Provider',
      providerUrl: 'https://example.com',
      tags: 'alpha, beta',
      extensions: '{\n  "region": "cn-hangzhou"\n}',
      status: 'disable',
      protocolEditorKind: 'a2a',
      agentCard: '',
      customProtocol: '',
      customProtocolVersion: '',
      customDescriptorMediaType: 'application/json',
      customNativeDescriptor: '{}',
      endpointSourceMode: 'declared-runtime',
      declaredEndpoints: [],
      callInterfaces: '',
      basedOnVersion: '',
      author: '',
      changeDescription: '',
    });
    expect(metadataToEditorValues({
      namespaceId: 'public',
      agentName: 'minimal',
      status: 'enable',
    }).extensions).toBe('');
  });

  it('maps A2A, custom, and multi-interface content back to the right editor', () => {
    const a2a = { ...CALL_INTERFACES[0], protocol: 'A2A' };
    expect(callInterfacesToEditorValues([a2a])).toMatchObject({
      protocolEditorKind: 'a2a',
      agentCard: '{\n  "name": "demo"\n}',
    });
    expect(callInterfacesToEditorValues(CALL_INTERFACES)).toEqual({
      protocolEditorKind: 'custom',
      customProtocol: 'custom',
      customProtocolVersion: '1.0.0',
      customDescriptorMediaType: 'application/json',
      customNativeDescriptor: '{\n  "name": "demo"\n}',
      endpointSourceMode: 'runtime-declared',
      declaredEndpoints: [],
      callInterfaces: callInterfacesToText(CALL_INTERFACES),
    });
    expect(callInterfacesToEditorValues([
      { ...CALL_INTERFACES[0], endpointSourceOrder: ['DECLARED'] },
    ])).toMatchObject({ endpointSourceMode: 'declared-only' });
    expect(callInterfacesToEditorValues([
      { ...CALL_INTERFACES[0], endpointSourceOrder: ['RUNTIME'] },
    ])).toMatchObject({ endpointSourceMode: 'runtime-only' });
    expect(callInterfacesToEditorValues([
      { ...CALL_INTERFACES[0], endpointSourceOrder: ['DECLARED', 'RUNTIME'] },
    ])).toMatchObject({ endpointSourceMode: 'declared-runtime' });
    expect(callInterfacesToEditorValues([])).toEqual({
      protocolEditorKind: 'raw',
      callInterfaces: '[]',
    });
    expect(callInterfacesToEditorValues([
      CALL_INTERFACES[0],
      { ...CALL_INTERFACES[0], protocol: 'other' },
    ])).toMatchObject({ protocolEditorKind: 'raw' });
  });

  it('defines lifecycle actions and discovery display helpers', () => {
    expect(getVersionActions('draft')).toEqual([
      'editDraft',
      'submit',
      'forcePublish',
      'deleteDraft',
    ]);
    expect(getVersionActions('reviewing')).toEqual(['forcePublish']);
    expect(getVersionActions('reviewed')).toEqual(['publish', 'forcePublish', 'redraft']);
    expect(getVersionActions('online')).toEqual(['offline']);
    expect(getVersionActions('offline')).toEqual(['online']);

    const duplicateInterfaces = [
      ...CALL_INTERFACES,
      { ...CALL_INTERFACES[0], protocolVersion: '2.0.0' },
      { ...CALL_INTERFACES[0], protocol: 'mcp' },
    ];
    expect(getProtocols(duplicateInterfaces)).toEqual(['custom', 'mcp']);
    expect(usesRuntimeSource(CALL_INTERFACES[0])).toBe(true);
    expect(usesRuntimeSource({
      ...CALL_INTERFACES[0],
      endpointSourceOrder: ['DECLARED'],
    })).toBe(false);
    expect(usesRuntimeSource(undefined)).toBe(false);
    expect(runtimeCacheKey('1.0.0', 'custom')).toBe('1.0.0@@custom');
    expect(namingDetailPath({
      namespaceId: 'tenant a',
      groupName: 'AI_GROUP',
      serviceName: 'service/name',
    })).toBe('/serviceDetail?serviceName=service%2Fname&groupName=AI_GROUP&namespace=tenant+a');
    expect(callInterfacesToText(CALL_INTERFACES)).toBe(JSON.stringify(CALL_INTERFACES, null, 2));
  });
});
