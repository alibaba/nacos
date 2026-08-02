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

import type {
  AgentCallInterface,
  AgentDraftCreateData,
  AgentDraftUpdateData,
  AgentMetadata,
  AgentMetadataUpdateData,
  AgentVersionStatus,
  NamingServiceRef,
} from '@/types/agent';

export type AgentEditorMode = 'create' | 'metadata' | 'draft-create' | 'draft-edit';

export type DraftContentMode = 'direct' | 'copy';

export type ProtocolEditorKind = 'a2a' | 'custom' | 'raw';

export type StructuredProtocolEditorKind = Exclude<ProtocolEditorKind, 'raw'>;

export type EndpointSourceMode =
  | 'declared-runtime'
  | 'runtime-declared'
  | 'declared-only'
  | 'runtime-only';

export interface DeclaredEndpointEditorValue {
  uri: string;
  transport: string;
}

export interface StructuredProtocolEditorValues {
  protocolEditorKind: StructuredProtocolEditorKind;
  agentCard: string;
  customProtocol: string;
  customProtocolVersion: string;
  customDescriptorMediaType: string;
  customNativeDescriptor: string;
  endpointSourceMode: EndpointSourceMode;
  declaredEndpoints: DeclaredEndpointEditorValue[];
}

export interface A2aImportProjection {
  agentName: string;
  version: string;
  displayName: string;
  description: string;
  providerName: string;
  providerUrl: string;
  tags: string;
  protocolEditor: StructuredProtocolEditorValues;
}

export type AgentVersionAction =
  | 'submit'
  | 'publish'
  | 'forcePublish'
  | 'redraft'
  | 'online'
  | 'offline'
  | 'editDraft'
  | 'deleteDraft';

export interface AgentEditorValues {
  agentName: string;
  version: string;
  displayName: string;
  description: string;
  iconUrl: string;
  providerName: string;
  providerUrl: string;
  tags: string;
  extensions: string;
  status: 'enable' | 'disable';
  protocolEditorKind: ProtocolEditorKind;
  agentCard: string;
  customProtocol: string;
  customProtocolVersion: string;
  customDescriptorMediaType: string;
  customNativeDescriptor: string;
  endpointSourceMode: EndpointSourceMode;
  declaredEndpoints: DeclaredEndpointEditorValue[];
  callInterfaces: string;
  basedOnVersion: string;
  author: string;
  changeDescription: string;
}

function required(value: string, name: string): string {
  const result = value.trim();
  if (!result) {
    throw new Error(`${name} is required`);
  }
  return result;
}

function parseJson(value: string, name: string): unknown {
  try {
    return JSON.parse(value);
  } catch {
    throw new Error(`${name} must be valid JSON`);
  }
}

function parseAgentCardJson(value: string): unknown {
  const withoutTrailingCommas = value.replace(
    /("(?:\\.|[^"\\])*")|,\s*([}\]])/g,
    (match, quoted, closing) => quoted || closing || match,
  );
  return parseJson(withoutTrailingCommas, 'agentCard');
}

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && !Array.isArray(value) && typeof value === 'object';
}

function optionalString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function validateTransport(value: string): string {
  const transport = required(value, 'transport');
  if (!/^[0-9A-Za-z+-]{1,64}$/.test(transport)) {
    throw new Error('transport must contain 1 to 64 letters, digits, +, or -');
  }
  return transport;
}

function endpointKey(uri: string, transport: string): string {
  let parsed: URL;
  try {
    parsed = new URL(uri);
  } catch {
    throw new Error(`Invalid Endpoint URI: ${uri}`);
  }
  if (!parsed.protocol || !parsed.hostname || parsed.username || parsed.password || parsed.hash) {
    throw new Error(`Invalid Endpoint URI: ${uri}`);
  }
  let port = parsed.port;
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

function endpointSourceOrder(mode: EndpointSourceMode): AgentCallInterface['endpointSourceOrder'] {
  switch (mode) {
    case 'declared-runtime':
      return ['DECLARED', 'RUNTIME'];
    case 'runtime-declared':
      return ['RUNTIME', 'DECLARED'];
    case 'declared-only':
      return ['DECLARED'];
    case 'runtime-only':
      return ['RUNTIME'];
  }
}

function endpointSourceMode(
  order: AgentCallInterface['endpointSourceOrder'],
): EndpointSourceMode {
  const key = order.join(',');
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

function normalizeA2aInterface(
  value: unknown,
  fallbackTransport?: string,
  fallbackVersion?: string,
): Record<string, unknown> {
  if (!isObject(value)) {
    throw new Error('AgentCard interface must be a JSON object');
  }
  const url = required(optionalString(value.url) || '', 'AgentCard interface url');
  endpointKey(url, optionalString(value.protocolBinding)
    || optionalString(value.transport)
    || fallbackTransport
    || '');
  const protocolBinding = validateTransport(
    optionalString(value.protocolBinding)
      || optionalString(value.transport)
      || fallbackTransport
      || '',
  );
  const protocolVersion = required(
    optionalString(value.protocolVersion) || fallbackVersion || '',
    'AgentCard interface protocolVersion',
  );
  return {
    ...value,
    url,
    transport: protocolBinding,
    protocolBinding,
    protocolVersion,
  };
}

function normalizeA2aAgentCard(
  agentName: string,
  version: string,
  text: string,
): { card: Record<string, unknown>; declaredEndpoints: AgentCallInterface['declaredEndpoints'] } {
  const parsed = parseAgentCardJson(required(text, 'agentCard'));
  if (!isObject(parsed)) {
    throw new Error('agentCard must be a JSON object');
  }
  const card: Record<string, unknown> = { ...parsed, name: agentName, version };
  const topProtocolVersion = optionalString(card.protocolVersion);
  const preferredTransport = optionalString(card.preferredTransport);
  let interfaces: Record<string, unknown>[];
  if (Array.isArray(card.supportedInterfaces) && card.supportedInterfaces.length > 0) {
    interfaces = card.supportedInterfaces.map((item) => normalizeA2aInterface(
      item,
      preferredTransport,
      topProtocolVersion,
    ));
  } else {
    const preferred = normalizeA2aInterface({
      url: card.url,
      protocolBinding: preferredTransport,
      protocolVersion: topProtocolVersion,
    });
    interfaces = [preferred];
    if (Array.isArray(card.additionalInterfaces)) {
      interfaces.push(...card.additionalInterfaces.map((item) => normalizeA2aInterface(
        item,
        optionalString(preferred.protocolBinding),
        optionalString(preferred.protocolVersion),
      )));
    }
  }
  const preferred = interfaces[0];
  card.supportedInterfaces = interfaces;
  card.url = preferred.url;
  card.preferredTransport = preferred.protocolBinding;
  card.protocolVersion = preferred.protocolVersion;
  card.additionalInterfaces = interfaces.slice(1);
  card.description ??= '';
  card.capabilities ??= {};
  card.defaultInputModes ??= [];
  card.defaultOutputModes ??= [];
  card.skills ??= [];

  const seen = new Set<string>();
  const declaredEndpoints = interfaces.flatMap((item) => {
    const uri = String(item.url);
    const transport = String(item.protocolBinding);
    const key = endpointKey(uri, transport);
    if (seen.has(key)) {
      return [];
    }
    seen.add(key);
    return [{ uri, transport }];
  });
  return { card, declaredEndpoints };
}

function editorFromValues(values: AgentEditorValues): StructuredProtocolEditorValues {
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
    declaredEndpoints: values.declaredEndpoints,
  };
}

function buildStructuredCallInterface(
  agentName: string,
  version: string,
  editor: StructuredProtocolEditorValues,
): AgentCallInterface {
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
    'nativeDescriptor',
  );
  if (nativeDescriptor === null) {
    throw new Error('nativeDescriptor must not be JSON null');
  }
  const seen = new Set<string>();
  const declaredEndpoints = editor.declaredEndpoints.flatMap((endpoint) => {
    if (!endpoint.uri.trim() && !endpoint.transport.trim()) {
      return [];
    }
    const uri = required(endpoint.uri, 'Endpoint uri');
    const transport = validateTransport(endpoint.transport);
    const key = endpointKey(uri, transport);
    if (seen.has(key)) {
      return [];
    }
    seen.add(key);
    return [{ uri, transport }];
  });
  return {
    protocol: required(editor.customProtocol, 'protocol'),
    protocolVersion: editor.customProtocolVersion.trim() || undefined,
    descriptorMediaType: required(editor.customDescriptorMediaType, 'descriptorMediaType'),
    nativeDescriptor,
    endpointSourceOrder: endpointSourceOrder(editor.endpointSourceMode),
    declaredEndpoints: declaredEndpoints.length > 0 ? declaredEndpoints : undefined,
  };
}

function validateUniqueProtocols(callInterfaces: AgentCallInterface[]): AgentCallInterface[] {
  const protocols = new Set<string>();
  for (const callInterface of callInterfaces) {
    if (protocols.has(callInterface.protocol)) {
      throw new Error(`protocol must be unique: ${callInterface.protocol}`);
    }
    protocols.add(callInterface.protocol);
  }
  return callInterfaces;
}

function serializeOptionalObject(value: string, name: string): string | undefined {
  if (!value.trim()) {
    return undefined;
  }
  const parsed = parseJson(value, name);
  if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error(`${name} must be a JSON object`);
  }
  return JSON.stringify(parsed);
}

function serializeCallInterfaces(
  values: AgentEditorValues,
  protocolEditors?: StructuredProtocolEditorValues[],
): string {
  const agentName = required(values.agentName, 'agentName');
  const version = required(values.version, 'version');
  if (protocolEditors) {
    if (protocolEditors.length === 0) {
      throw new Error('at least one protocol is required');
    }
    return JSON.stringify(validateUniqueProtocols(protocolEditors.map(
      (editor) => buildStructuredCallInterface(agentName, version, editor),
    )));
  }
  if (values.protocolEditorKind !== 'raw') {
    return JSON.stringify([buildStructuredCallInterface(
      agentName,
      version,
      editorFromValues(values),
    )]);
  }
  const parsed = parseJson(
    required(values.callInterfaces, 'callInterfaces'),
    'callInterfaces',
  );
  if (!Array.isArray(parsed) || parsed.length === 0) {
    throw new Error('callInterfaces must be a non-empty JSON array');
  }
  return JSON.stringify(parsed);
}

function serializeProvider(values: AgentEditorValues): string | undefined {
  const name = values.providerName.trim();
  const url = values.providerUrl.trim();
  if (!name && !url) {
    return undefined;
  }
  if (!name) {
    throw new Error('providerName is required when providerUrl is set');
  }
  return JSON.stringify(url ? { name, url } : { name });
}

function serializeTags(value: string): string | undefined {
  const tags = value.split(',').map((item) => item.trim()).filter(Boolean);
  return tags.length > 0 ? JSON.stringify(tags) : undefined;
}

export function buildDraftCreateData(
  namespaceId: string,
  values: AgentEditorValues,
  initial: boolean,
  contentMode: DraftContentMode,
  protocolEditors?: StructuredProtocolEditorValues[],
): AgentDraftCreateData {
  const result: AgentDraftCreateData = {
    namespaceId,
    agentName: required(values.agentName, 'agentName'),
    version: required(values.version, 'version'),
    author: values.author.trim() || undefined,
    changeDescription: values.changeDescription.trim() || undefined,
  };
  if (contentMode === 'direct') {
    result.callInterfaces = serializeCallInterfaces(values, protocolEditors);
  } else {
    result.basedOnVersion = required(values.basedOnVersion, 'basedOnVersion');
  }
  if (initial) {
    result.displayName = values.displayName.trim() || undefined;
    result.description = values.description.trim() || undefined;
    result.iconUrl = values.iconUrl.trim() || undefined;
    result.provider = serializeProvider(values);
    result.tags = serializeTags(values.tags);
    result.extensions = serializeOptionalObject(values.extensions, 'extensions');
  }
  return result;
}

export function createStructuredProtocolEditor(
  kind: StructuredProtocolEditorKind = 'a2a',
  agentCard = '{}',
): StructuredProtocolEditorValues {
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

export function projectA2aAgentCard(
  text: string,
  fallbackVersion = '',
): A2aImportProjection {
  const parsed = parseAgentCardJson(required(text, 'agentCard'));
  if (!isObject(parsed)) {
    throw new Error('agentCard must be a JSON object');
  }
  const agentName = required(optionalString(parsed.name) || '', 'AgentCard name');
  const version = required(
    optionalString(parsed.version) || fallbackVersion,
    'AgentCard version',
  );
  const normalized = normalizeA2aAgentCard(agentName, version, text);
  const provider: Record<string, unknown> = isObject(parsed.provider) ? parsed.provider : {};
  const providerName = optionalString(provider.organization)
    || optionalString(provider.name)
    || '';
  const providerUrl = optionalString(provider.url) || '';
  const tags = new Set<string>();
  if (Array.isArray(parsed.skills)) {
    for (const skill of parsed.skills) {
      if (isObject(skill) && Array.isArray(skill.tags)) {
        for (const tag of skill.tags) {
          if (typeof tag === 'string' && tag.trim()) {
            tags.add(tag.trim());
          }
        }
      }
    }
  }
  return {
    agentName,
    version,
    displayName: agentName,
    description: optionalString(parsed.description) || '',
    providerName,
    providerUrl,
    tags: [...tags].join(', '),
    protocolEditor: createStructuredProtocolEditor(
      'a2a',
      JSON.stringify(normalized.card, null, 2),
    ),
  };
}

export function buildDraftUpdateData(
  namespaceId: string,
  values: AgentEditorValues,
): AgentDraftUpdateData {
  return {
    namespaceId,
    agentName: required(values.agentName, 'agentName'),
    version: required(values.version, 'version'),
    callInterfaces: serializeCallInterfaces(values),
    changeDescription: values.changeDescription.trim() || undefined,
  };
}

export function buildMetadataUpdateData(
  namespaceId: string,
  values: AgentEditorValues,
): AgentMetadataUpdateData {
  return {
    namespaceId,
    agentName: required(values.agentName, 'agentName'),
    displayName: values.displayName.trim() || undefined,
    description: values.description.trim() || undefined,
    iconUrl: values.iconUrl.trim() || undefined,
    provider: serializeProvider(values),
    tags: serializeTags(values.tags),
    extensions: serializeOptionalObject(values.extensions, 'extensions'),
    status: values.status,
  };
}

export function metadataToEditorValues(agent: AgentMetadata): AgentEditorValues {
  return {
    agentName: agent.agentName,
    version: '',
    displayName: agent.displayName || '',
    description: agent.description || '',
    iconUrl: agent.iconUrl || '',
    providerName: agent.provider?.name || '',
    providerUrl: agent.provider?.url || '',
    tags: agent.tags?.join(', ') || '',
    extensions: agent.extensions ? JSON.stringify(agent.extensions, null, 2) : '',
    status: agent.status,
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
  };
}

export function callInterfacesToText(callInterfaces: AgentCallInterface[]): string {
  return JSON.stringify(callInterfaces, null, 2);
}

export function callInterfacesToEditorValues(
  callInterfaces: AgentCallInterface[],
): Partial<AgentEditorValues> {
  if (callInterfaces.length !== 1) {
    return {
      protocolEditorKind: 'raw',
      callInterfaces: callInterfacesToText(callInterfaces),
    };
  }
  const callInterface = callInterfaces[0];
  if (callInterface.protocol.toLowerCase() === 'a2a') {
    return {
      protocolEditorKind: 'a2a',
      agentCard: JSON.stringify(callInterface.nativeDescriptor, null, 2),
      callInterfaces: callInterfacesToText(callInterfaces),
    };
  }
  return {
    protocolEditorKind: 'custom',
    customProtocol: callInterface.protocol,
    customProtocolVersion: callInterface.protocolVersion || '',
    customDescriptorMediaType: callInterface.descriptorMediaType,
    customNativeDescriptor: JSON.stringify(callInterface.nativeDescriptor, null, 2),
    endpointSourceMode: endpointSourceMode(callInterface.endpointSourceOrder),
    declaredEndpoints: (callInterface.declaredEndpoints || []).map((endpoint) => ({
      uri: endpoint.uri,
      transport: endpoint.transport,
    })),
    callInterfaces: callInterfacesToText(callInterfaces),
  };
}

export function getVersionActions(status: AgentVersionStatus): AgentVersionAction[] {
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
  }
}

export function getProtocols(callInterfaces: AgentCallInterface[]): string[] {
  return [...new Set(callInterfaces.map((item) => item.protocol))];
}

export function usesRuntimeSource(callInterface: AgentCallInterface | undefined): boolean {
  return callInterface?.endpointSourceOrder.includes('RUNTIME') === true;
}

export function runtimeCacheKey(version: string, protocol: string): string {
  return `${version}@@${protocol}`;
}

export function namingDetailPath(ref: NamingServiceRef): string {
  const params = new URLSearchParams({
    serviceName: ref.serviceName,
    groupName: ref.groupName,
    namespace: ref.namespaceId,
  });
  return `/serviceDetail?${params.toString()}`;
}
