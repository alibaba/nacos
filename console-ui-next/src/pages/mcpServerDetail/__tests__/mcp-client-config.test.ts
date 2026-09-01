import { describe, expect, it } from 'vitest';
import type { McpServerDetailInfo } from '@/types/mcp';
import { buildMcpClientConfigurations } from '../mcp-client-config';

function server(overrides: Partial<McpServerDetailInfo> = {}): McpServerDetailInfo {
  return {
    name: 'demo-server',
    protocol: 'mcp-sse',
    frontProtocol: 'mcp-sse',
    enabled: true,
    ...overrides,
  };
}

describe('MCP client configuration', () => {
  it('uses the resolved frontend endpoint when one is available', () => {
    const configurations = buildMcpClientConfigurations(server({
      frontendEndpoints: [{
        protocol: 'https', address: 'gateway.example.com', port: '443', path: 'sse',
      }],
      backendEndpoints: [{
        protocol: 'http', address: '10.0.0.8', port: '8080', path: '/sse',
      }],
    }));

    expect(configurations).toEqual([{
      key: 'endpoint-0',
      label: 'https://gateway.example.com/sse',
      config: {
        mcpServers: {
          'demo-server': { url: 'https://gateway.example.com/sse' },
        },
      },
    }]);
  });

  it('falls back to the backend endpoint for a native MCP server', () => {
    const [configuration] = buildMcpClientConfigurations(server({
      backendEndpoints: [{
        protocol: 'http', address: '1.1.1.1', port: '3306', path: '/sse',
      }],
    }));

    expect(configuration.config).toEqual({
      mcpServers: {
        'demo-server': { url: 'http://1.1.1.1:3306/sse' },
      },
    });
  });

  it('does not expose a REST backend as the MCP client endpoint', () => {
    expect(buildMcpClientConfigurations(server({
      protocol: 'http',
      backendEndpoints: [{
        protocol: 'http', address: '10.0.0.8', port: '8080', path: '/api',
      }],
    }))).toEqual([]);
  });

  it('wraps a stdio server block in the MCP client configuration shape', () => {
    const [configuration] = buildMcpClientConfigurations(server({
      protocol: 'stdio',
      frontProtocol: 'stdio',
      localServerConfig: { command: 'npx', args: ['-y', '@example/mcp-server'] },
    }));

    expect(configuration.config).toEqual({
      mcpServers: {
        'demo-server': { command: 'npx', args: ['-y', '@example/mcp-server'] },
      },
    });
  });

  it('keeps an already wrapped stdio client configuration unchanged', () => {
    const wrapped = { mcpServers: { existing: { command: 'uvx', args: ['demo'] } } };
    const [configuration] = buildMcpClientConfigurations(server({
      protocol: 'stdio',
      frontProtocol: 'stdio',
      localServerConfig: wrapped,
    }));

    expect(configuration.config).toBe(wrapped);
  });

  it('builds directly usable configurations from stdio packages', () => {
    const [configuration] = buildMcpClientConfigurations(server({
      protocol: 'stdio',
      frontProtocol: 'stdio',
      packages: [{
        identifier: '@example/mcp-server',
        version: '1.2.0',
        registryType: 'npm',
        environmentVariables: [{ name: 'API_KEY', default: 'replace-me' }],
      }],
    }));

    expect(configuration.config).toEqual({
      mcpServers: {
        'demo-server': {
          command: 'npx',
          args: ['-y', '@example/mcp-server@1.2.0'],
          env: { API_KEY: 'replace-me' },
        },
      },
    });
  });

  it('uses the legacy client-config placeholders for missing environment values', () => {
    const [configuration] = buildMcpClientConfigurations(server({
      protocol: 'stdio',
      frontProtocol: 'stdio',
      packages: [{
        identifier: '@example/mcp-server',
        registryType: 'npm',
        environmentVariables: [
          { name: 'API_KEY' },
          { name: 'SERVICE_URL' },
          { name: 'SERVER_PORT' },
          { name: 'REGION' },
        ],
      }],
    }));

    expect(configuration.config).toMatchObject({
      mcpServers: {
        'demo-server': {
          env: {
            API_KEY: 'YOUR_API_KEY_HERE',
            SERVICE_URL: 'https://api.example.com',
            SERVER_PORT: '3000',
            REGION: '<REGION>',
          },
        },
      },
    });
  });

  it('keeps legacy argument placeholders and variable substitution', () => {
    const [configuration] = buildMcpClientConfigurations(server({
      protocol: 'stdio',
      frontProtocol: 'stdio',
      packages: [{
        identifier: '@example/mcp-server',
        registryType: 'npm',
        runtimeArguments: [
          { type: 'named', name: '--token', value: '' },
          {
            type: 'positional',
            value: '{workspace}/server.js',
            variables: { workspace: { default: '/opt/mcp' } },
          },
        ],
        packageArguments: [{ type: 'positional', value: '', value_hint: 'config-path' }],
      }],
    } as unknown as Partial<McpServerDetailInfo>));

    expect(configuration.config).toMatchObject({
      mcpServers: {
        'demo-server': {
          args: [
            '--token=<value>',
            '/opt/mcp/server.js',
            '-y',
            '@example/mcp-server',
            '<config-path>',
          ],
        },
      },
    });
  });
});
