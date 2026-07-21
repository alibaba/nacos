type ConfigListState = {
  dataId?: string;
  groupName?: string;
  appName?: string;
  searchMode?: string;
  pageNo?: number;
  pageSize?: number;
};

type ServiceListState = {
  serviceNameParam?: string;
  groupNameParam?: string;
  ignoreEmptyService?: boolean;
  pageNo?: number;
  pageSize?: number;
};

const setOptional = (params: URLSearchParams, key: string, value?: string | null) => {
  if (value) {
    params.set(key, value);
  }
};

const setNonDefaultNumber = (
  params: URLSearchParams,
  key: string,
  value: number | undefined,
  defaultValue: number
) => {
  if (value && value !== defaultValue) {
    params.set(key, String(value));
  }
};

export const appendConfigListState = (params: URLSearchParams, state: ConfigListState) => {
  setOptional(params, 'listDataId', state.dataId);
  setOptional(params, 'listGroupName', state.groupName);
  setOptional(params, 'listAppName', state.appName);
  if (state.searchMode && state.searchMode !== 'blur') {
    params.set('listSearchMode', state.searchMode);
  }
  setNonDefaultNumber(params, 'listPageNo', state.pageNo, 1);
  setNonDefaultNumber(params, 'listPageSize', state.pageSize, 10);
};

export const appendServiceListState = (params: URLSearchParams, state: ServiceListState) => {
  setOptional(params, 'listServiceNameParam', state.serviceNameParam);
  setOptional(params, 'listGroupNameParam', state.groupNameParam);
  if (state.ignoreEmptyService === false) {
    params.set('listIgnoreEmptyService', 'false');
  }
  setNonDefaultNumber(params, 'listPageNo', state.pageNo, 1);
  setNonDefaultNumber(params, 'listPageSize', state.pageSize, 10);
};

export const buildConfigDetailPath = (
  dataId: string,
  group: string,
  namespace: string,
  state: ConfigListState
) => {
  const params = new URLSearchParams({ dataId, group, namespace });
  appendConfigListState(params, state);
  return `/configdetail?${params.toString()}`;
};

export const buildServiceDetailPath = (
  serviceName: string,
  groupName: string,
  namespace: string,
  state: ServiceListState
) => {
  const params = new URLSearchParams({ serviceName, groupName, namespace });
  appendServiceListState(params, state);
  return `/serviceDetail?${params.toString()}`;
};

export const buildConfigListPathFromDetail = (searchParams: URLSearchParams) => {
  const params = new URLSearchParams();
  setOptional(params, 'dataId', searchParams.get('listDataId'));
  setOptional(params, 'groupName', searchParams.get('listGroupName'));
  setOptional(params, 'appName', searchParams.get('listAppName'));
  setOptional(params, 'searchMode', searchParams.get('listSearchMode'));
  setOptional(params, 'pageNo', searchParams.get('listPageNo'));
  setOptional(params, 'pageSize', searchParams.get('listPageSize'));
  const query = params.toString();
  return query ? `/configurationManagement?${query}` : '/configurationManagement';
};

export const buildServiceListPathFromDetail = (
  searchParams: URLSearchParams,
  namespaceId: string
) => {
  const params = new URLSearchParams();
  setOptional(params, 'namespace', namespaceId);
  setOptional(params, 'serviceNameParam', searchParams.get('listServiceNameParam'));
  setOptional(params, 'groupNameParam', searchParams.get('listGroupNameParam'));
  setOptional(params, 'ignoreEmptyService', searchParams.get('listIgnoreEmptyService'));
  setOptional(params, 'pageNo', searchParams.get('listPageNo'));
  setOptional(params, 'pageSize', searchParams.get('listPageSize'));
  const query = params.toString();
  return query ? `/serviceManagement?${query}` : '/serviceManagement';
};
