export default function(model) {
  const configurationMenu = {
    key: 'configurationManagementVirtual',
    children: [
      {
        key: 'configurationManagement',
        url: '/configurationManagement',
      },
      {
        key: 'listeningToQuery',
        url: '/listeningToQuery',
      },
    ],
  };
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
    {
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
    },
    {
      key: 'namespace',
      url: '/namespace',
    },
  ].filter(item => item);
}
