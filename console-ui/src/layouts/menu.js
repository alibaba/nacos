/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { isJsonString } from '../utils/nacosutil';

const serviceDiscoveryMenu = {
  key: 'serviceManagementVirtual',
  children: [
    {
      key: 'serviceManagement',
      url: '/serviceManagement',
    },
    {
      key: 'subscriberList',
      url: '/subscriberList',
    },
  ],
};
const configurationMenu = {
  key: 'configurationManagementVirtual',
  children: [
    {
      key: 'configurationManagement',
      url: '/configurationManagement',
    },
    {
      key: 'historyRollback',
      url: '/historyRollback',
    },
    {
      key: 'listeningToQuery',
      url: '/listeningToQuery',
    },
  ],
};

export const McpServerManagementRoute = '/mcpServerManagement';

// AI Registry 菜单，包含 MCP Registry、Agent Registry、Skill Registry、Prompt Registry
const aiRegistryMenu = {
  key: 'aiRegistry',
  badge: 'new',
  children: [
    {
      key: 'mcpRegistry',
      url: McpServerManagementRoute,
    },
    {
      key: 'agentRegistry',
      url: '/agentManagement',
    },
    {
      key: 'skillRegistry',
      url: '/skillManagement',
    },
    {
      key: 'promptRegistry',
      url: '/promptManagement',
    },
  ],
};

/**
 * 权限控制相关
 */
const authorityControlMenu = {
  key: 'authorityControl',
  children: [
    {
      key: 'userList',
      url: '/userManagement',
    },
    {
      key: 'roleManagement',
      url: '/rolesManagement',
    },
    {
      key: 'privilegeManagement',
      url: '/permissionsManagement',
    },
  ],
};
const namespaceMenu = {
  key: 'namespace',
  url: '/namespace',
};
const clusterMenu = {
  key: 'clusterManagementVirtual',
  children: [
    {
      key: 'clusterManagement',
      url: '/clusterManagement',
    },
  ],
};
const settingMenu = {
  key: 'settingCenter',
  url: '/settingCenter',
};

const pluginMenu = {
  key: 'pluginManagement',
  badge: 'new',
  url: '/pluginManagement',
};

const agentManagementMenu = {
  key: 'agentManagement',
  badge: 'new',
  url: '/agentManagement',
  children: [
    {
      key: 'agentList',
      url: '/agentManagement',
    },
  ],
};

export default function(model, aiEnabled = true) {
  const { token = '{}' } = localStorage;
  const { globalAdmin } = isJsonString(token) ? JSON.parse(token) || {} : {};
  const result = [];
  if (model === 'naming') {
    result.push(serviceDiscoveryMenu);
  } else if (model === 'config') {
    result.push(configurationMenu);
  } else if (model === 'microservice') {
    result.push(configurationMenu, serviceDiscoveryMenu);
  } else if (model === 'ai') {
    result.push(aiRegistryMenu, pluginMenu);
  } else {
    result.push(configurationMenu, serviceDiscoveryMenu);
    if (aiEnabled) {
      result.push(aiRegistryMenu);
    }
    result.push(pluginMenu);
  }
  if (globalAdmin) {
    result.push(authorityControlMenu);
  }
  result.push(namespaceMenu);
  result.push(clusterMenu);
  result.push(settingMenu);
  return result;
}
