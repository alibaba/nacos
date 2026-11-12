import { describe, expect, it } from 'vitest';

import {
  buildCopilotConfigPayload,
  hasLegacyConnection,
  type CopilotConnectionInfo,
  type CopilotConnectionSource,
} from '../copilot-config';

const deploymentConnection: CopilotConnectionInfo = {
  name: 'prod-qwen',
  source: 'DEPLOYMENT',
  enabled: true,
  available: true,
};

const legacyConnection: CopilotConnectionInfo = {
  name: 'legacy',
  source: 'LEGACY',
  enabled: true,
  available: true,
  apiKeyConfigured: true,
};

describe('copilot config payload', () => {
  it('submits only the connection selection when no legacy connection is visible', () => {
    expect(
      buildCopilotConfigPayload({
        selectedConnection: 'prod-qwen',
        connections: [deploymentConnection],
        apiKey: 'st******ey',
        model: 'qwen-max',
      })
    ).toEqual({ selectedConnection: 'prod-qwen' });
  });

  it('submits the legacy api key and model when a legacy connection is visible', () => {
    expect(
      buildCopilotConfigPayload({
        selectedConnection: '',
        connections: [deploymentConnection, legacyConnection],
        apiKey: 'st******ey',
        model: 'qwen-plus',
      })
    ).toEqual({ selectedConnection: '', apiKey: 'st******ey', model: 'qwen-plus' });
  });

  it('clears the selection with an empty value and trims explicit names', () => {
    expect(
      buildCopilotConfigPayload({
        selectedConnection: '  ',
        connections: [deploymentConnection],
      })
    ).toEqual({ selectedConnection: '' });

    expect(
      buildCopilotConfigPayload({
        selectedConnection: '  prod-qwen  ',
        connections: [deploymentConnection],
      })
    ).toEqual({ selectedConnection: 'prod-qwen' });
  });

  it('keeps an unchanged masked legacy key so the backend preserves it', () => {
    expect(
      buildCopilotConfigPayload({
        selectedConnection: 'legacy',
        connections: [legacyConnection],
        apiKey: 'te******34',
        model: 'qwen-turbo',
      }).apiKey
    ).toBe('te******34');
  });

  it('detects the legacy connection regardless of case or order', () => {
    expect(hasLegacyConnection([deploymentConnection])).toBe(false);
    expect(hasLegacyConnection([deploymentConnection, legacyConnection])).toBe(true);
    expect(
      hasLegacyConnection([
        { ...legacyConnection, source: 'legacy' as CopilotConnectionSource },
      ])
    ).toBe(true);
    expect(hasLegacyConnection(undefined)).toBe(false);
  });
});
