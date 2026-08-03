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

export const DEFAULT_AGENT_CARD = JSON.stringify(
  {
    name: '',
    version: '1.0.0',
    description: '',
    supportedInterfaces: [
      {
        url: 'https://agent.example.com/a2a',
        protocolBinding: 'HTTP+JSON',
        protocolVersion: '0.3',
      },
    ],
    capabilities: { streaming: false, pushNotifications: false },
    defaultInputModes: ['text/plain'],
    defaultOutputModes: ['text/plain'],
    skills: [],
  },
  null,
  2
);

export const DEFAULT_CALL_INTERFACES = JSON.stringify(
  [
    {
      protocol: 'custom',
      protocolVersion: '1.0.0',
      descriptorMediaType: 'application/json',
      nativeDescriptor: {},
      endpointSourceOrder: ['RUNTIME', 'DECLARED'],
      declaredEndpoints: [],
    },
  ],
  null,
  2
);

function required(value, name) {
  const result = String(value || '').trim();
  if (!result) {
    throw new Error(`${name} is required`);
  }
  return result;
}

function parseJson(value, name) {
  try {
    return JSON.parse(value);
  } catch (e) {
    throw new Error(`${name} must be valid JSON`);
  }
}

function parseAgentCardJson(value) {
  const withoutTrailingCommas = value.replace(
    /("(?:\\.|[^"\\])*")|,\s*([}\]])/g,
    (match, quoted, closing) => quoted || closing || match
  );
  return parseJson(withoutTrailingCommas, 'agentCard');
}

function isObject(value) {
  return value !== null && !Array.isArray(value) && typeof value === 'object';
}

function optionalString(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function validateTransport(value) {
  const transport = required(value, 'transport');
  if (!/^[0-9A-Za-z+-]{1,64}$/.test(transport)) {
    throw new Error('transport must contain 1 to 64 letters, digits, +, or -');
  }
  return transport;
}

function endpointKey(uri, transport) {
  let parsed;
  try {
    parsed = new URL(uri);
  } catch (e) {
    throw new Error(`Invalid Endpoint URI: ${uri}`);
  }
  if (!parsed.protocol || !parsed.hostname || parsed.username || parsed.password || parsed.hash) {
    throw new Error(`Invalid Endpoint URI: ${uri}`);
  }
  let { port } = parsed;
  if (!port) {
    if (parsed.protocol === 'http:' || parsed.protocol === 'ws:') {
      port = '80';
    } else if (parsed.protocol === 'https:' || parsed.protocol === 'wss:') {
      port = '443';
    } else {
      throw new Error(`Invalid Endpoint URI: ${uri}`);
    }
  }
  return `${parsed.hostname.toLowerCase()}@@${port}@@${transport}`;
}

function sourceOrder(mode) {
  switch (mode) {
    case 'runtime-declared':
      return ['RUNTIME', 'DECLARED'];
    case 'declared-only':
      return ['DECLARED'];
    case 'runtime-only':
      return ['RUNTIME'];
    default:
      return ['DECLARED', 'RUNTIME'];
  }
}

function sourceMode(order) {
  const key = (order || []).join(',');
  switch (key) {
    case 'RUNTIME,DECLARED':
      return 'runtime-declared';
    case 'DECLARED':
      return 'declared-only';
    case 'RUNTIME':
      return 'runtime-only';
    default:
      return 'declared-runtime';
  }
}

function normalizeA2aInterface(value, fallbackTransport, fallbackVersion) {
  if (!isObject(value)) {
    throw new Error('AgentCard interface must be a JSON object');
  }
  const url = required(optionalString(value.url) || '', 'AgentCard interface url');
  const protocolBinding = validateTransport(
    optionalString(value.protocolBinding) ||
      optionalString(value.transport) ||
      fallbackTransport ||
      ''
  );
  endpointKey(url, protocolBinding);
  const protocolVersion = required(
    optionalString(value.protocolVersion) || fallbackVersion || '',
    'AgentCard interface protocolVersion'
  );
  return { ...value, url, transport: protocolBinding, protocolBinding, protocolVersion };
}

function normalizeA2aAgentCard(agentName, version, text) {
  const parsed = parseAgentCardJson(required(text, 'agentCard'));
  if (!isObject(parsed)) {
    throw new Error('agentCard must be a JSON object');
  }
  const card = { ...parsed, name: agentName, version };
  const topProtocolVersion = optionalString(card.protocolVersion);
  const preferredTransport = optionalString(card.preferredTransport);
  let interfaces;
  if (Array.isArray(card.supportedInterfaces) && card.supportedInterfaces.length > 0) {
    interfaces = card.supportedInterfaces.map(item => {
      return normalizeA2aInterface(item, preferredTransport, topProtocolVersion);
    });
  } else {
    const preferred = normalizeA2aInterface(
      {
        url: card.url,
        protocolBinding: preferredTransport,
        protocolVersion: topProtocolVersion,
      },
      undefined,
      undefined
    );
    interfaces = [preferred];
    if (Array.isArray(card.additionalInterfaces)) {
      interfaces.push(
        ...card.additionalInterfaces.map(item => {
          return normalizeA2aInterface(item, preferred.protocolBinding, preferred.protocolVersion);
        })
      );
    }
  }
  const preferred = interfaces[0];
  card.supportedInterfaces = interfaces;
  card.url = preferred.url;
  card.preferredTransport = preferred.protocolBinding;
  card.protocolVersion = preferred.protocolVersion;
  card.additionalInterfaces = interfaces.slice(1);
  if (card.description === undefined || card.description === null) card.description = '';
  if (card.capabilities === undefined || card.capabilities === null) card.capabilities = {};
  if (card.defaultInputModes === undefined || card.defaultInputModes === null) {
    card.defaultInputModes = [];
  }
  if (card.defaultOutputModes === undefined || card.defaultOutputModes === null) {
    card.defaultOutputModes = [];
  }
  if (card.skills === undefined || card.skills === null) card.skills = [];

  const seen = new Set();
  const declaredEndpoints = [];
  interfaces.forEach(item => {
    const uri = String(item.url);
    const transport = String(item.protocolBinding);
    const key = endpointKey(uri, transport);
    if (!seen.has(key)) {
      seen.add(key);
      declaredEndpoints.push({ uri, transport });
    }
  });
  return { card, declaredEndpoints };
}

function editorFromValues(values) {
  if (values.protocolEditorKind === 'raw') {
    throw new Error('raw callInterfaces cannot be converted to a structured protocol');
  }
  return {
    protocolEditorKind: values.protocolEditorKind,
    agentCard: values.agentCard,
    customProtocol: values.customProtocol,
    customProtocolVersion: values.customProtocolVersion,
    customDescriptorMediaType: values.customDescriptorMediaType,
    customNativeDescriptor: values.customNativeDescriptor,
    endpointSourceMode: values.endpointSourceMode,
    declaredEndpoints: values.declaredEndpoints || [],
  };
}

function buildStructuredCallInterface(agentName, version, editor) {
  if (editor.protocolEditorKind === 'a2a') {
    const normalized = normalizeA2aAgentCard(agentName, version, editor.agentCard);
    return {
      protocol: 'a2a',
      protocolVersion: String(normalized.card.protocolVersion),
      descriptorMediaType: 'application/json',
      nativeDescriptor: normalized.card,
      endpointSourceOrder: ['DECLARED', 'RUNTIME'],
      declaredEndpoints: normalized.declaredEndpoints,
    };
  }
  const nativeDescriptor = parseJson(
    required(editor.customNativeDescriptor, 'nativeDescriptor'),
    'nativeDescriptor'
  );
  if (nativeDescriptor === null) {
    throw new Error('nativeDescriptor must not be JSON null');
  }
  const seen = new Set();
  const declaredEndpoints = [];
  (editor.declaredEndpoints || []).forEach(endpoint => {
    if (!String(endpoint.uri || '').trim() && !String(endpoint.transport || '').trim()) {
      return;
    }
    const uri = required(endpoint.uri, 'Endpoint uri');
    const transport = validateTransport(endpoint.transport);
    const key = endpointKey(uri, transport);
    if (!seen.has(key)) {
      seen.add(key);
      declaredEndpoints.push({ uri, transport });
    }
  });
  const result = {
    protocol: required(editor.customProtocol, 'protocol'),
    protocolVersion: String(editor.customProtocolVersion || '').trim() || undefined,
    descriptorMediaType: required(editor.customDescriptorMediaType, 'descriptorMediaType'),
    nativeDescriptor,
    endpointSourceOrder: sourceOrder(editor.endpointSourceMode),
  };
  if (declaredEndpoints.length > 0) result.declaredEndpoints = declaredEndpoints;
  return result;
}

function serializeOptionalObject(value, name) {
  if (!String(value || '').trim()) {
    return undefined;
  }
  const parsed = parseJson(value, name);
  if (!isObject(parsed)) {
    throw new Error(`${name} must be a JSON object`);
  }
  return JSON.stringify(parsed);
}

function validateUniqueProtocols(callInterfaces) {
  const protocols = new Set();
  callInterfaces.forEach(callInterface => {
    if (protocols.has(callInterface.protocol)) {
      throw new Error(`protocol must be unique: ${callInterface.protocol}`);
    }
    protocols.add(callInterface.protocol);
  });
  return callInterfaces;
}

function serializeCallInterfaces(values, protocolEditors) {
  const agentName = required(values.agentName, 'agentName');
  const version = required(values.version, 'version');
  if (protocolEditors) {
    if (protocolEditors.length === 0) {
      throw new Error('at least one protocol is required');
    }
    return JSON.stringify(
      validateUniqueProtocols(
        protocolEditors.map(editor => buildStructuredCallInterface(agentName, version, editor))
      )
    );
  }
  if (values.protocolEditorKind === 'a2a' || values.protocolEditorKind === 'custom') {
    return JSON.stringify([
      buildStructuredCallInterface(agentName, version, editorFromValues(values)),
    ]);
  }
  const parsed = parseJson(required(values.callInterfaces, 'callInterfaces'), 'callInterfaces');
  if (!Array.isArray(parsed) || parsed.length === 0) {
    throw new Error('callInterfaces must be a non-empty JSON array');
  }
  return JSON.stringify(parsed);
}

function serializeProvider(values) {
  const name = String(values.providerName || '').trim();
  const url = String(values.providerUrl || '').trim();
  if (!name && !url) {
    return undefined;
  }
  if (!name) {
    throw new Error('providerName is required when providerUrl is set');
  }
  return JSON.stringify(url ? { name, url } : { name });
}

function serializeTags(value) {
  const tags = String(value || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean);
  return tags.length > 0 ? JSON.stringify(tags) : undefined;
}

export function buildDraftCreateData(namespaceId, values, initial, contentMode, protocolEditors) {
  const result = {
    namespaceId,
    agentName: required(values.agentName, 'agentName'),
    version: required(values.version, 'version'),
    author: String(values.author || '').trim() || undefined,
    changeDescription: String(values.changeDescription || '').trim() || undefined,
  };
  if (contentMode === 'direct') {
    result.callInterfaces = serializeCallInterfaces(values, protocolEditors);
  } else {
    result.basedOnVersion = required(values.basedOnVersion, 'basedOnVersion');
  }
  if (initial) {
    result.displayName = String(values.displayName || '').trim() || undefined;
    result.description = String(values.description || '').trim() || undefined;
    result.iconUrl = String(values.iconUrl || '').trim() || undefined;
    result.provider = serializeProvider(values);
    result.tags = serializeTags(values.tags);
    result.extensions = serializeOptionalObject(values.extensions, 'extensions');
  }
  return result;
}

export function createStructuredProtocolEditor(kind = 'a2a', agentCard = '{}') {
  return {
    protocolEditorKind: kind,
    agentCard,
    customProtocol: '',
    customProtocolVersion: '',
    customDescriptorMediaType: 'application/json',
    customNativeDescriptor: '{}',
    endpointSourceMode: 'declared-runtime',
    declaredEndpoints: [{ uri: '', transport: 'HTTP' }],
  };
}

export function projectA2aAgentCard(text, fallbackVersion = '') {
  const parsed = parseAgentCardJson(required(text, 'agentCard'));
  if (!isObject(parsed)) {
    throw new Error('agentCard must be a JSON object');
  }
  const agentName = required(optionalString(parsed.name) || '', 'AgentCard name');
  const version = required(optionalString(parsed.version) || fallbackVersion, 'AgentCard version');
  const normalized = normalizeA2aAgentCard(agentName, version, text);
  const provider = isObject(parsed.provider) ? parsed.provider : {};
  const providerName = optionalString(provider.organization) || optionalString(provider.name) || '';
  const providerUrl = optionalString(provider.url) || '';
  const tags = new Set();
  if (Array.isArray(parsed.skills)) {
    parsed.skills.forEach(skill => {
      if (isObject(skill) && Array.isArray(skill.tags)) {
        skill.tags.forEach(tag => {
          if (typeof tag === 'string' && tag.trim()) {
            tags.add(tag.trim());
          }
        });
      }
    });
  }
  return {
    agentName,
    version,
    displayName: agentName,
    description: optionalString(parsed.description) || '',
    providerName,
    providerUrl,
    tags: Array.from(tags).join(', '),
    protocolEditor: createStructuredProtocolEditor('a2a', JSON.stringify(normalized.card, null, 2)),
  };
}

export function buildDraftUpdateData(namespaceId, values) {
  return {
    namespaceId,
    agentName: required(values.agentName, 'agentName'),
    version: required(values.version, 'version'),
    callInterfaces: serializeCallInterfaces(values),
    changeDescription: String(values.changeDescription || '').trim() || undefined,
  };
}

export function buildMetadataUpdateData(namespaceId, values) {
  return {
    namespaceId,
    agentName: required(values.agentName, 'agentName'),
    displayName: String(values.displayName || '').trim() || undefined,
    description: String(values.description || '').trim() || undefined,
    iconUrl: String(values.iconUrl || '').trim() || undefined,
    provider: serializeProvider(values),
    tags: serializeTags(values.tags),
    extensions: serializeOptionalObject(values.extensions, 'extensions'),
    status: values.status,
  };
}

export function metadataToFormValues(agent) {
  return {
    agentName: agent.agentName,
    displayName: agent.displayName || '',
    description: agent.description || '',
    iconUrl: agent.iconUrl || '',
    providerName: (agent.provider && agent.provider.name) || '',
    providerUrl: (agent.provider && agent.provider.url) || '',
    tags: (agent.tags || []).join(', '),
    extensions: agent.extensions ? JSON.stringify(agent.extensions, null, 2) : '',
    status: agent.status,
  };
}

export function callInterfacesToFormValues(callInterfaces) {
  if (!Array.isArray(callInterfaces) || callInterfaces.length !== 1) {
    return {
      protocolEditorKind: 'raw',
      callInterfaces: JSON.stringify(callInterfaces || [], null, 2),
    };
  }
  const callInterface = callInterfaces[0];
  if (String(callInterface.protocol || '').toLowerCase() === 'a2a') {
    return {
      protocolEditorKind: 'a2a',
      agentCard: JSON.stringify(callInterface.nativeDescriptor, null, 2),
      callInterfaces: JSON.stringify(callInterfaces, null, 2),
    };
  }
  return {
    protocolEditorKind: 'custom',
    customProtocol: callInterface.protocol,
    customProtocolVersion: callInterface.protocolVersion || '',
    customDescriptorMediaType: callInterface.descriptorMediaType,
    customNativeDescriptor: JSON.stringify(callInterface.nativeDescriptor, null, 2),
    endpointSourceMode: sourceMode(callInterface.endpointSourceOrder),
    declaredEndpoints: (callInterface.declaredEndpoints || []).map(endpoint => ({
      uri: endpoint.uri,
      transport: endpoint.transport,
    })),
    callInterfaces: JSON.stringify(callInterfaces, null, 2),
  };
}

export function getVersionActions(status) {
  switch (status) {
    case 'draft':
      return ['editDraft', 'submit', 'forcePublish', 'deleteDraft'];
    case 'reviewing':
      return ['forcePublish'];
    case 'reviewed':
      return ['publish', 'forcePublish', 'redraft'];
    case 'online':
      return ['offline'];
    case 'offline':
      return ['online'];
    default:
      return [];
  }
}

export function getProtocols(callInterfaces) {
  return Array.from(new Set((callInterfaces || []).map(item => item.protocol)));
}

export function usesRuntimeSource(callInterface) {
  return !!(
    callInterface &&
    Array.isArray(callInterface.endpointSourceOrder) &&
    callInterface.endpointSourceOrder.includes('RUNTIME')
  );
}

export function runtimeCacheKey(version, protocol) {
  return `${version}@@${protocol}`;
}

export function namingDetailPath(ref) {
  const params = new URLSearchParams({
    namespaceId: ref.namespaceId,
    groupName: ref.groupName,
    name: ref.serviceName,
  });
  return `/serviceDetail?${params.toString()}`;
}
