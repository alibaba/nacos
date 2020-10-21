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

export default function(model) {
  const { token = '{}' } = localStorage;
  const { globalAdmin } = isJsonString(token) ? JSON.parse(token) || {} : {};

  return [
    model === 'naming' ? undefined : configurationMenu,
    {
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
    },
    globalAdmin ? authorityControlMenu : undefined,
    {
      key: 'namespace',
      url: '/namespace',
    },
    {
      key: 'clusterManagementVirtual',
      children: [
        {
          key: 'clusterManagement',
          url: '/clusterManagement',
        },
      ],
    },
  ].filter(item => item);
}
