import client from './client';
import type { ApiResult } from './types';

export interface PluginInfo {
  pluginId: string;
  pluginName: string;
  pluginType: string;
  enabled: boolean;
  critical: boolean;
  configurable: boolean;
  typeCritical: boolean;
  executionMode: 'EXCLUSIVE' | 'CHAIN' | 'ROUTED' | 'BROADCAST';
  exclusive: boolean;
  availableNodeCount: number;
  totalNodeCount: number;
}

export type PluginConfigSource =
  | 'DEFAULT'
  | 'STATIC'
  | 'RUNTIME_PERSISTED'
  | 'LOCAL_ONLY';

export interface PluginConfigItemDefinition {
  key: string;
  name: string;
  description?: string;
  defaultValue?: string;
  type: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'ENUM';
  required: boolean;
  enumValues?: string[];
  aliases?: string[];
  sensitive: boolean;
  effectMode: 'RUNTIME' | 'RESTART';
}

export interface PluginConfigValueMeta {
  key: string;
  source: PluginConfigSource;
  overridden: boolean;
}

export interface PluginDetail extends PluginInfo {
  config: Record<string, string>;
  configDefinitions: PluginConfigItemDefinition[];
  configValueMetas: Record<string, PluginConfigValueMeta>;
}

export const pluginApi = {
  list: (pluginType?: string): ApiResult<PluginInfo[]> =>
    client.get('v3/console/plugin/list', { params: pluginType ? { pluginType } : {} }) as ApiResult<PluginInfo[]>,

  setStatus: (params: {
    pluginType: string;
    pluginName: string;
    enabled: boolean;
    localOnly?: boolean;
  }): ApiResult<boolean> =>
    client.put('v3/console/plugin/status', null, { params }) as ApiResult<boolean>,

  detail: (pluginType: string, pluginName: string): ApiResult<PluginDetail> =>
    client.get('v3/console/plugin', {
      params: { pluginType, pluginName },
    }) as ApiResult<PluginDetail>,

  availability: (
    pluginType: string,
    pluginName: string,
  ): ApiResult<Record<string, boolean>> =>
    client.get('v3/console/plugin/availability', {
      params: { pluginType, pluginName },
    }) as ApiResult<Record<string, boolean>>,

  updateConfig: (params: {
    pluginType: string;
    pluginName: string;
    config: Record<string, string>;
    localOnly: boolean;
  }): ApiResult<string> =>
    client.put('v3/console/plugin/config', params) as ApiResult<string>,
};
