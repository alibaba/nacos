// ===== Service List =====

export interface ServiceView {
  name: string;
  groupName: string;
  clusterCount: number;
  ipCount: number;
  healthyInstanceCount: number;
  triggerFlag: boolean;
}

export interface ServiceListParams {
  namespaceId: string;
  serviceNameParam?: string;
  groupNameParam?: string;
  ignoreEmptyService?: boolean;
  pageNo: number;
  pageSize: number;
}

export interface ServiceListResponse {
  totalCount: number;
  pageItems: ServiceView[];
}

// ===== Service Detail =====

export interface HealthChecker {
  type: 'TCP' | 'HTTP' | 'NONE';
  path?: string;
  headers?: string;
}

export interface ClusterInfo {
  healthChecker: HealthChecker;
  metadata: Record<string, string>;
  hosts: Instance[];
  healthyCheckPort: number;
  useInstancePortForCheck: boolean;
}

export interface Selector {
  type: string;
  expression?: string;
}

export interface ServiceDetailInfo {
  serviceName: string;
  groupName: string;
  namespaceId: string;
  protectThreshold: number;
  metadata: Record<string, string>;
  selector: Selector;
  ephemeral: boolean;
  clusterMap: Record<string, ClusterInfo>;
}

// ===== Instance =====

export interface Instance {
  ip: string;
  port: number;
  weight: number;
  healthy: boolean;
  enabled: boolean;
  ephemeral: boolean;
  clusterName: string;
  serviceName: string;
  metadata: Record<string, string>;
}

export interface InstanceListResponse {
  totalCount: number;
  pageItems: Instance[];
}

// ===== Form Data =====

export interface ServiceFormData {
  namespaceId: string;
  serviceName: string;
  groupName: string;
  ephemeral?: boolean;
  protectThreshold: number;
  metadata?: string;
  selector?: string;
}

export interface ClusterUpdateData {
  namespaceId: string;
  serviceName: string;
  groupName: string;
  clusterName: string;
  checkPort: number;
  useInstancePort4Check: boolean;
  healthChecker: string;
  metadata?: string;
}

export interface InstanceUpdateData {
  namespaceId: string;
  serviceName: string;
  groupName: string;
  ip: string;
  port: number;
  clusterName: string;
  weight: number;
  enabled: boolean;
  ephemeral: boolean;
  metadata?: string;
}

export interface InstanceDeleteData {
  namespaceId: string;
  serviceName: string;
  groupName: string;
  ip: string;
  port: number;
  clusterName: string;
  ephemeral: boolean;
}

// ===== Subscriber =====

export interface SubscriberInfo {
  subscriberName: string;
  groupName: string;
  serviceName: string;
  namespaceId: string;
  subscribeCount: number;
  clusters: string;
}

export interface SubscriberListResponse {
  totalCount: number;
  pageItems: SubscriberInfo[];
}
