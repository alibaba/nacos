import { describe, expect, it } from 'vitest';

import {
  buildCopilotConfigPayload,
  isConnectionUsable,
  isSelectionOverriddenByDeployment,
  type CopilotConnectionInfo,
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
  describe.each([
    { name: 'missing runtime table', connections: undefined },
    { name: 'empty runtime table', connections: [] },
    { name: 'deployment-only table', connections: [deploymentConnection] },
    {
      name: 'deployment connection replacing legacy',
      connections: [{ ...deploymentConnection, name: 'legacy' }],
    },
  ])('$name', ({ connections }) => {
    it('submits legacy key and model updates independently of runtime entries', () => {
      expect(
        buildCopilotConfigPayload({
          selectedConnection: '',
          connections,
          apiKey: 'local-test-key',
          model: 'qwen-max',
        })
      ).toEqual({ selectedConnection: '', apiKey: 'local-test-key', model: 'qwen-max' });
    });

    it('keeps the masked stored key when submitting a model update', () => {
      expect(
        buildCopilotConfigPayload({
          selectedConnection: '',
          connections,
          apiKey: 'st******ey',
          model: 'qwen-plus',
        })
      ).toEqual({ selectedConnection: '', apiKey: 'st******ey', model: 'qwen-plus' });
    });

    it('submits blank legacy fields to clear stored values', () => {
      expect(
        buildCopilotConfigPayload({
          selectedConnection: '',
          connections,
          apiKey: '',
          model: '',
        })
      ).toEqual({ selectedConnection: '', apiKey: '', model: '' });
    });
  });

  it('omits unsubmitted legacy fields rather than clearing them', () => {
    expect(buildCopilotConfigPayload({ selectedConnection: '', apiKey: 'local-test-key' }))
      .toEqual({ selectedConnection: '', apiKey: 'local-test-key' });
    expect(buildCopilotConfigPayload({ selectedConnection: '', model: 'qwen-max' }))
      .toEqual({ selectedConnection: '', model: 'qwen-max' });
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

  it('treats only enabled and available connections as usable', () => {
    expect(isConnectionUsable(deploymentConnection)).toBe(true);
    expect(isConnectionUsable({ ...deploymentConnection, enabled: false })).toBe(false);
    expect(isConnectionUsable({ ...deploymentConnection, available: false })).toBe(false);
  });

  it('reports a deployment-owned selection regardless of the stored value', () => {
    expect(
      isSelectionOverriddenByDeployment({
        selectedConnection: 'alpha',
        effectiveSelectedConnection: 'beta',
        selectedConnectionSource: 'DEPLOYMENT',
      })
    ).toBe(true);
    expect(
      isSelectionOverriddenByDeployment({
        selectedConnection: '',
        effectiveSelectedConnection: 'beta',
        selectedConnectionSource: 'DEPLOYMENT',
      })
    ).toBe(true);
    expect(
      isSelectionOverriddenByDeployment({
        selectedConnection: 'alpha',
        effectiveSelectedConnection: 'alpha',
        selectedConnectionSource: 'STORED',
      })
    ).toBe(false);
    expect(isSelectionOverriddenByDeployment({})).toBe(false);
  });
});
