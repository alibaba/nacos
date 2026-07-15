import client from './client';
import type { ApiResult } from './types';

export interface PluginInfo {
  pluginId: string;
  pluginName: string;
  pluginType: string;
  enabled: boolean;
  critical: boolean;
  configurable: boolean;
  exclusive: boolean;
  availableNodeCount: number;
  totalNodeCount: number;
}

export const pluginApi = {
  list: (pluginType?: string): ApiResult<PluginInfo[]> =>
    client.get('v3/console/plugin/list', { params: pluginType ? { pluginType } : {} }) as ApiResult<PluginInfo[]>,

  setStatus: (params: {
    pluginType: string;
    pluginName: string;
    enabled: boolean;
  }): ApiResult<boolean> =>
    client.put('v3/console/plugin/status', null, { params }) as ApiResult<boolean>,
};
