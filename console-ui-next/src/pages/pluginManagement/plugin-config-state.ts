import type {
  PluginConfigItemDefinition,
  PluginConfigSource,
  PluginDetail,
} from '@/api/plugin';

export type UpdatablePluginConfigSource = 'RUNTIME_PERSISTED' | 'LOCAL_ONLY';

export type PluginConfigValidationError =
  | 'required'
  | 'number'
  | 'boolean'
  | 'enum';

const DECIMAL_PATTERN = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/;

export function getRuntimeDefinitions(
  detail: PluginDetail,
): PluginConfigItemDefinition[] {
  return (detail.configDefinitions || []).filter(
    definition => definition.effectMode === 'RUNTIME',
  );
}

export function createEffectiveDraft(detail: PluginDetail): Record<string, string> {
  const result: Record<string, string> = {};
  for (const definition of detail.configDefinitions || []) {
    result[definition.key] =
      detail.config?.[definition.key] ?? definition.defaultValue ?? '';
  }
  return result;
}

export function getSourceSnapshot(
  detail: PluginDetail,
  source: UpdatablePluginConfigSource,
): Record<string, string> {
  const result: Record<string, string> = {};
  for (const definition of detail.configDefinitions || []) {
    if (detail.configValueMetas?.[definition.key]?.source !== source) {
      continue;
    }
    const value = detail.config?.[definition.key];
    if (value !== undefined) {
      result[definition.key] = value;
    }
  }
  return result;
}

export function hasLocalOnlyOverrides(detail: PluginDetail): boolean {
  return Object.values(detail.configValueMetas || {}).some(
    meta => meta.source === 'LOCAL_ONLY',
  );
}

export function buildSourceUpdate(
  detail: PluginDetail,
  source: UpdatablePluginConfigSource,
  draft: Record<string, string>,
  dirtyKeys: ReadonlySet<string>,
  resetKeys: ReadonlySet<string>,
): Record<string, string> {
  const result = getSourceSnapshot(detail, source);
  const runtimeKeys = new Set(getRuntimeDefinitions(detail).map(definition => definition.key));

  for (const key of resetKeys) {
    if (runtimeKeys.has(key)) {
      delete result[key];
    }
  }
  for (const key of dirtyKeys) {
    if (runtimeKeys.has(key) && !resetKeys.has(key)) {
      result[key] = draft[key] ?? '';
    }
  }
  return result;
}

export function validatePluginConfigValue(
  definition: PluginConfigItemDefinition,
  value: string,
): PluginConfigValidationError | null {
  if (definition.required && value.length === 0) {
    return 'required';
  }
  if (definition.type === 'NUMBER' && !DECIMAL_PATTERN.test(value)) {
    return 'number';
  }
  if (
    definition.type === 'BOOLEAN'
    && value.toLowerCase() !== 'true'
    && value.toLowerCase() !== 'false'
  ) {
    return 'boolean';
  }
  if (
    definition.type === 'ENUM'
    && definition.enumValues?.length
    && !definition.enumValues.includes(value)
  ) {
    return 'enum';
  }
  return null;
}

export function getSourceLabelKey(source?: PluginConfigSource): string {
  switch (source) {
    case 'STATIC':
      return 'plugin.sourceStatic';
    case 'RUNTIME_PERSISTED':
      return 'plugin.sourceRuntime';
    case 'LOCAL_ONLY':
      return 'plugin.sourceLocalOnly';
    case 'DEFAULT':
    default:
      return 'plugin.sourceDefault';
  }
}
