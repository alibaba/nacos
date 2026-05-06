export type ConfigType = 'text' | 'json' | 'xml' | 'yaml' | 'html' | 'properties' | 'toml';

export interface Config {
  id?: string;
  dataId: string;
  groupName: string;
  content: string;
  md5: string;
  type: ConfigType;
  appName: string;
  configTags: string;
  desc: string;
  createTime: string;
  modifyTime: string;
}

export interface ConfigListParams {
  dataId?: string;
  groupName?: string;
  appName?: string;
  configTags?: string;
  type?: string;
  search?: 'blur' | 'accurate';
  configDetail?: string;
  pageNo: number;
  pageSize: number;
  namespaceId: string;
}

export interface ConfigListResponse {
  totalCount: number;
  pageNumber: number;
  pagesAvailable: number;
  pageItems: Config[];
}

export interface ConfigCreateData {
  dataId: string;
  groupName: string;
  content: string;
  desc?: string;
  configTags?: string;
  type: ConfigType;
  appName?: string;
  namespaceId: string;
}

export interface ConfigHistory {
  id: string;
  nid: string;
  dataId: string;
  groupName: string;
  content: string;
  md5: string;
  type: ConfigType;
  appName: string;
  srcUser: string;
  srcIp: string;
  opType: string;
  publishType: string;
  extInfo: string;
  createdTime: string;
  modifyTime: string;
}

export interface ConfigHistoryListResponse {
  totalCount: number;
  pageNumber: number;
  pagesAvailable: number;
  pageItems: ConfigHistory[];
}

export type ConflictPolicy = 'ABORT' | 'SKIP' | 'OVERWRITE';

export interface ConfigCloneItem {
  cfgId: string;
  dataId: string;
  group: string;
}

export interface ConfigListenerInfo {
  listenersStatus: Record<string, string>;
  queryType: 'config' | 'ip';
}

export interface ConfigBetaInfo {
  dataId: string;
  groupName: string;
  content: string;
  type: string;
  appName: string;
  desc: string;
  configTags: string;
  grayRule: string;
  md5: string;
}

export const CONFIG_TYPES: { value: ConfigType; label: string }[] = [
  { value: 'text', label: 'TEXT' },
  { value: 'json', label: 'JSON' },
  { value: 'xml', label: 'XML' },
  { value: 'yaml', label: 'YAML' },
  { value: 'html', label: 'HTML' },
  { value: 'properties', label: 'Properties' },
  { value: 'toml', label: 'TOML' },
];

export const MONACO_LANGUAGE_MAP: Record<ConfigType, string> = {
  text: 'plaintext',
  json: 'json',
  xml: 'xml',
  yaml: 'yaml',
  html: 'html',
  properties: 'ini',
  toml: 'ini',
};
