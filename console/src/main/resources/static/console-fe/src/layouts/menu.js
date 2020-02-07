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
    {
      key: 'clusterManagementVirtual',
      children: [
        {
          key: 'clusterManagement',
          url: '/clusterManagement',
        },
      ],
    },
    globalAdmin ? authorityControlMenu : undefined,
    {
      key: 'namespace',
      url: '/namespace',
    },
  ].filter(item => item);
}
