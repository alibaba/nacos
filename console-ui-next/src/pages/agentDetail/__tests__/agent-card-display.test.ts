import { describe, expect, it } from 'vitest';

import type { AgentDetailInfo } from '@/types/agent';

import { mapAgentCardForDisplay } from '../agent-card-display';

describe('AgentCard display mapper', () => {
  it('prefers supportedInterfaces as primary display source', () => {
    const agent: AgentDetailInfo = {
      name: 'agent-a',
      version: '1.0.0',
      url: 'http://legacy-url',
      preferredTransport: 'LEGACY',
      protocolVersion: '0.2.9',
      supportedInterfaces: [
        {
          url: 'https://example.com/a/jsonrpc',
          protocolBinding: 'JSONRPC',
          protocolVersion: '1.0',
          tenant: 'public',
        },
      ],
      capabilities: {
        extendedAgentCard: true,
      },
    };

    const display = mapAgentCardForDisplay(agent);

    expect(display.serviceUrl).toBe('https://example.com/a/jsonrpc');
    expect(display.transport).toBe('JSONRPC');
    expect(display.protocolVersion).toBe('1.0');
    expect(display.extendedCardSupported).toBe(true);
    expect(display.interfaceList).toHaveLength(1);
  });

  it('shows empty display fields when supportedInterfaces are missing', () => {
    const agent: AgentDetailInfo = {
      name: 'agent-b',
      version: '1.0.0',
      url: 'https://legacy.example.com/b',
      preferredTransport: 'JSONRPC',
      protocolVersion: '0.3.0',
      supportsAuthenticatedExtendedCard: true,
    };

    const display = mapAgentCardForDisplay(agent);

    expect(display.serviceUrl).toBe('');
    expect(display.transport).toBe('');
    expect(display.protocolVersion).toBe('');
    expect(display.extendedCardSupported).toBe(false);
    expect(display.interfaceList).toHaveLength(0);
  });
});
