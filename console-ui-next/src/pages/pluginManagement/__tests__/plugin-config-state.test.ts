import { describe, expect, it } from 'vitest';

import type { PluginDetail } from '@/api/plugin';

import {
  buildSourceUpdate,
  createEffectiveDraft,
  getRuntimeDefinitions,
  getSourceLabelKey,
  getSourceSnapshot,
  hasLocalOnlyOverrides,
  validatePluginConfigValue,
} from '../plugin-config-state';

const detail: PluginDetail = {
  pluginId: 'auth:nacos',
  pluginType: 'auth',
  pluginName: 'nacos',
  enabled: true,
  critical: true,
  configurable: true,
  typeCritical: true,
  executionMode: 'EXCLUSIVE',
  exclusive: true,
  availableNodeCount: 1,
  totalNodeCount: 1,
  config: {
    runtime: 'runtime-value',
    static: 'static-value',
    local: 'local-value',
    restart: 'restart-value',
  },
  configDefinitions: [
    {
      key: 'runtime',
      name: 'Runtime',
      type: 'STRING',
      required: false,
      sensitive: false,
      effectMode: 'RUNTIME',
    },
    {
      key: 'static',
      name: 'Static',
      type: 'NUMBER',
      required: true,
      sensitive: false,
      effectMode: 'RUNTIME',
    },
    {
      key: 'local',
      name: 'Local',
      type: 'BOOLEAN',
      required: false,
      sensitive: false,
      effectMode: 'RUNTIME',
    },
    {
      key: 'restart',
      name: 'Restart',
      type: 'STRING',
      required: false,
      sensitive: true,
      effectMode: 'RESTART',
    },
    {
      key: 'default-only',
      name: 'Default',
      defaultValue: 'fallback',
      type: 'ENUM',
      required: false,
      enumValues: ['fallback', 'other'],
      sensitive: false,
      effectMode: 'RUNTIME',
    },
  ],
  configValueMetas: {
    runtime: { key: 'runtime', source: 'RUNTIME_PERSISTED', overridden: true },
    static: { key: 'static', source: 'STATIC', overridden: false },
    local: { key: 'local', source: 'LOCAL_ONLY', overridden: true },
    restart: { key: 'restart', source: 'RUNTIME_PERSISTED', overridden: false },
    'default-only': {
      key: 'default-only',
      source: 'DEFAULT',
      overridden: false,
    },
  },
};

describe('plugin config state', () => {
  it('creates a draft from effective values and definition defaults', () => {
    expect(createEffectiveDraft(detail)).toEqual({
      runtime: 'runtime-value',
      static: 'static-value',
      local: 'local-value',
      restart: 'restart-value',
      'default-only': 'fallback',
    });
  });

  it('returns only runtime-editable definitions', () => {
    expect(getRuntimeDefinitions(detail).map(definition => definition.key)).toEqual([
      'runtime',
      'static',
      'local',
      'default-only',
    ]);
  });

  it('reconstructs the visible source snapshot without copying lower sources', () => {
    expect(getSourceSnapshot(detail, 'RUNTIME_PERSISTED')).toEqual({
      runtime: 'runtime-value',
      restart: 'restart-value',
    });
    expect(getSourceSnapshot(detail, 'LOCAL_ONLY')).toEqual({
      local: 'local-value',
    });
  });

  it('preserves unchanged source entries, applies dirty values, and omits resets', () => {
    const update = buildSourceUpdate(
      detail,
      'RUNTIME_PERSISTED',
      {
        ...detail.config,
        static: '42',
      },
      new Set(['static']),
      new Set(['runtime']),
    );

    expect(update).toEqual({
      static: '42',
      restart: 'restart-value',
    });
  });

  it('never changes restart fields from dirty or reset input', () => {
    const update = buildSourceUpdate(
      detail,
      'RUNTIME_PERSISTED',
      {
        ...detail.config,
        restart: 'changed',
      },
      new Set(['restart']),
      new Set(['restart']),
    );

    expect(update).toEqual({
      runtime: 'runtime-value',
      restart: 'restart-value',
    });
  });

  it('detects effective local-only overrides', () => {
    expect(hasLocalOnlyOverrides(detail)).toBe(true);
    expect(hasLocalOnlyOverrides({
      ...detail,
      configValueMetas: {
        runtime: {
          key: 'runtime',
          source: 'RUNTIME_PERSISTED',
          overridden: false,
        },
      },
    })).toBe(false);
  });

  it('validates required, number, boolean, and enum values', () => {
    const [runtime, number, bool, , enumDefinition] = detail.configDefinitions;

    expect(validatePluginConfigValue({ ...runtime, required: true }, '')).toBe('required');
    expect(validatePluginConfigValue(number, 'not-a-number')).toBe('number');
    expect(validatePluginConfigValue({ ...number, required: false }, '')).toBe('number');
    expect(validatePluginConfigValue(number, 'Infinity')).toBe('number');
    expect(validatePluginConfigValue(number, '12.5')).toBeNull();
    expect(validatePluginConfigValue(number, '1e3')).toBeNull();
    expect(validatePluginConfigValue(bool, 'yes')).toBe('boolean');
    expect(validatePluginConfigValue(bool, 'TRUE')).toBeNull();
    expect(validatePluginConfigValue(enumDefinition, 'invalid')).toBe('enum');
    expect(validatePluginConfigValue(enumDefinition, 'fallback')).toBeNull();
  });

  it('maps every source to its locale key', () => {
    expect(getSourceLabelKey('STATIC')).toBe('plugin.sourceStatic');
    expect(getSourceLabelKey('RUNTIME_PERSISTED')).toBe('plugin.sourceRuntime');
    expect(getSourceLabelKey('LOCAL_ONLY')).toBe('plugin.sourceLocalOnly');
    expect(getSourceLabelKey('DEFAULT')).toBe('plugin.sourceDefault');
    expect(getSourceLabelKey()).toBe('plugin.sourceDefault');
  });
});
