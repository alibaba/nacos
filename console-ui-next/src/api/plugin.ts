import client from './client';
import type { AxiosPromise } from 'axios';

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
  list: (pluginType?: string): AxiosPromise<PluginInfo[]> =>
    client.get('v3/console/plugin/list', { params: pluginType ? { pluginType } : {} }),

  setStatus: (params: {
    pluginType: string;
    pluginName: string;
    enabled: boolean;
  }): AxiosPromise<void> =>
    client.put('v3/console/plugin/status', null, { params }),
};
