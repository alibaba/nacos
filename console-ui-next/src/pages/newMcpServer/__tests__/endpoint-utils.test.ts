import { describe, expect, it } from 'vitest';
import type { McpServerDetailInfo } from '@/types/mcp';
import {
  buildUrlExportPath,
  buildEndpointUrl,
  isManagedDirectEndpointRef,
  resolveMcpEndpointUrl,
  shouldUseExistingService,
} from '@/lib/mcp-endpoint-utils';

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

  it('uses resolved endpoints instead of treating raw frontend configuration as an endpoint', () => {
    const detail: McpServerDetailInfo = {
      name: 'ad.inside/inside-ads',
      protocol: 'mcp-streamable',
      frontProtocol: 'mcp-streamable',
      enabled: true,
      remoteServerConfig: {
        exportPath: '/api/mcp',
        serviceRef: {
          namespaceId: 'public',
          groupName: 'mcp-endpoints',
          serviceName: 'ad.inside/inside-ads::1.0.0',
          transportProtocol: 'https',
        },
        frontEndpointConfigList: [{
          type: 'streamable-http',
          protocol: 'https',
          endpointType: 'BACKEND',
          endpointData: 'app.inside.ad:443',
          path: '/api/mcp',
        }],
      },
      backendEndpoints: [{
        protocol: 'https', address: 'app.inside.ad', port: '443', path: '/api/mcp',
      }],
    };

    expect(resolveMcpEndpointUrl(detail)).toBe('https://app.inside.ad/api/mcp');
    expect(resolveMcpEndpointUrl(detail)).not.toContain('undefined');
  });

  it('normalizes endpoint URLs for editable inputs', () => {
    expect(
      buildEndpointUrl({ protocol: 'https:', address: 'example.com', port: 443, path: 'mcp' })
    ).toBe('https://example.com/mcp');
    expect(buildEndpointUrl({ protocol: 'mcp-sse', address: 'example.com', port: '8080' }, 'http')).toBe(
      'http://example.com:8080'
    );
  });

  it('preserves query credentials in MCP endpoint URLs', () => {
    const url = new URL('https://mcp.amap.com/mcp?key=test-key');
    const exportPath = buildUrlExportPath(url);

    expect(exportPath).toBe('/mcp?key=test-key');
    expect(
      buildEndpointUrl({
        protocol: 'https',
        address: 'mcp.amap.com',
        port: '443',
        path: exportPath,
      })
    ).toBe('https://mcp.amap.com/mcp?key=test-key');
  });
});
