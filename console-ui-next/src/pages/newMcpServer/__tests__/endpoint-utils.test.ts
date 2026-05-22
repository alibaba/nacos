import { describe, expect, it } from 'vitest';
import type { McpServerDetailInfo } from '@/types/mcp';
import {
  buildEndpointUrl,
  isManagedDirectEndpointRef,
  resolveMcpEndpointUrl,
  shouldUseExistingService,
} from '../endpoint-utils';

describe('newMcpServer endpoint utils', () => {
  it('rebuilds MCP endpoint URL from the generated direct backend endpoint', () => {
    const detail: McpServerDetailInfo = {
      name: 'weather',
      protocol: 'mcp-sse',
      frontProtocol: 'mcp-sse',
      enabled: true,
      remoteServerConfig: {
        exportPath: '/sse',
        serviceRef: {
          namespaceId: 'public',
          groupName: 'mcp-endpoints',
          serviceName: 'weather::1.0.0',
          transportProtocol: 'http',
        },
      },
      backendEndpoints: [{ protocol: 'http', address: '127.0.0.1', port: '8080' }],
    };

    expect(isManagedDirectEndpointRef(detail.remoteServerConfig?.serviceRef)).toBe(true);
    expect(shouldUseExistingService(detail)).toBe(false);
    expect(resolveMcpEndpointUrl(detail)).toBe('http://127.0.0.1:8080/sse');
  });

  it('keeps user-selected services in existing-service mode', () => {
    const detail: McpServerDetailInfo = {
      name: 'weather',
      protocol: 'http',
      frontProtocol: 'mcp-sse',
      enabled: true,
      remoteServerConfig: {
        serviceRef: {
          namespaceId: 'public',
          groupName: 'DEFAULT_GROUP',
          serviceName: 'weather-backend',
          transportProtocol: 'https',
        },
      },
    };

    expect(isManagedDirectEndpointRef(detail.remoteServerConfig?.serviceRef)).toBe(false);
    expect(shouldUseExistingService(detail)).toBe(true);
  });

  it('normalizes endpoint URLs for editable inputs', () => {
    expect(
      buildEndpointUrl({ protocol: 'https:', address: 'example.com', port: 443, path: 'mcp' })
    ).toBe('https://example.com:443/mcp');
    expect(buildEndpointUrl({ protocol: 'mcp-sse', address: 'example.com', port: '8080' }, 'http')).toBe(
      'http://example.com:8080'
    );
  });
});
