import type { McpEndpointInfo, McpServerDetailInfo, McpServiceRef } from '@/types/mcp';

const MCP_DIRECT_ENDPOINT_GROUP = 'mcp-endpoints';
const DEFAULT_ENDPOINT_PROTOCOL = 'http';
const MCP_FRONT_PROTOCOLS = new Set(['stdio', 'mcp-sse', 'mcp-streamable']);

type EndpointLike = Omit<Partial<McpEndpointInfo>, 'port'> & {
  port?: string | number;
};

function normalizeProtocol(protocol?: string, fallback = DEFAULT_ENDPOINT_PROTOCOL) {
  const normalized = (protocol || fallback).replace(/:$/, '');
  return MCP_FRONT_PROTOCOLS.has(normalized) ? fallback : normalized;
}

function normalizeEndpointPath(path?: string) {
  if (!path || path === '/') {
    return '';
  }
  return path.startsWith('/') ? path : `/${path}`;
}

function normalizeEndpointPort(protocol: string, port?: string | number) {
  const rawPort = port === undefined || port === null ? '' : String(port).trim();
  if (
    !rawPort ||
    (protocol === 'https' && rawPort === '443') ||
    (protocol === 'http' && rawPort === '80')
  ) {
    return '';
  }
  return `:${rawPort}`;
}

export function buildUrlExportPath(url: URL) {
  return `${url.pathname || '/'}${url.search || ''}`;
}

export function isManagedDirectEndpointRef(serviceRef?: McpServiceRef) {
  return serviceRef?.groupName === MCP_DIRECT_ENDPOINT_GROUP;
}

export function shouldUseExistingService(data: McpServerDetailInfo) {
  const serviceRef = data.remoteServerConfig?.serviceRef;
  return !!serviceRef?.serviceName && !isManagedDirectEndpointRef(serviceRef);
}

export function buildEndpointUrl(
  endpoint?: EndpointLike,
  fallbackProtocol = DEFAULT_ENDPOINT_PROTOCOL,
  fallbackPath?: string
) {
  if (!endpoint) {
    return '';
  }
  const address = endpoint.address?.trim();
  if (!address) {
    return '';
  }
  const protocol = normalizeProtocol(endpoint.protocol, fallbackProtocol);
  const port = normalizeEndpointPort(protocol, endpoint.port);
  const path = normalizeEndpointPath(endpoint.path || fallbackPath);
  return `${protocol}://${address}${port}${path}`;
}

export function resolveMcpEndpointUrl(data: McpServerDetailInfo) {
  const serviceRef = data.remoteServerConfig?.serviceRef;
  const fallbackProtocol =
    serviceRef?.transportProtocol || data.backendEndpoints?.[0]?.protocol || DEFAULT_ENDPOINT_PROTOCOL;
  const endpoint =
    data.frontendEndpoints?.[0] ||
    data.remoteServerConfig?.frontEndpointConfigList?.[0] ||
    data.backendEndpoints?.[0];
  return buildEndpointUrl(endpoint, fallbackProtocol, data.remoteServerConfig?.exportPath);
}
