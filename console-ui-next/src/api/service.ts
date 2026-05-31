import client from './client';
import type { ApiResult } from './types';
import type {
  ServiceListParams,
  ServiceListResponse,
  ServiceDetailInfo,
  ServiceFormData,
  ClusterUpdateData,
  InstanceListResponse,
  InstanceDeleteData,
  InstanceUpdateData,
  SubscriberListResponse,
} from '@/types/service';

export const serviceApi = {
  // List services
  listServices: (params: ServiceListParams): ApiResult<ServiceListResponse> =>
    client.get('v3/console/ns/service/list', { params }) as ApiResult<ServiceListResponse>,

  // Get service detail
  getService: (params: { namespaceId: string; serviceName: string; groupName: string }): ApiResult<ServiceDetailInfo> =>
    client.get('v3/console/ns/service', { params }) as ApiResult<ServiceDetailInfo>,

  // Create service
  createService: (data: ServiceFormData): ApiResult<string> =>
    client.post('v3/console/ns/service', data) as ApiResult<string>,

  // Update service
  updateService: (data: ServiceFormData): ApiResult<string> =>
    client.put('v3/console/ns/service', data) as ApiResult<string>,

  // Delete service
  deleteService: (params: { namespaceId: string; serviceName: string; groupName: string }): ApiResult<string> =>
    client.delete('v3/console/ns/service', { params }) as ApiResult<string>,

  // Get selector types
  getSelectorTypes: (): ApiResult<string[]> =>
    client.get('v3/console/ns/service/selector/types') as ApiResult<string[]>,

  // Update cluster
  updateCluster: (data: ClusterUpdateData): ApiResult<string> =>
    client.put('v3/console/ns/service/cluster', data) as ApiResult<string>,

  // List instances
  listInstances: (params: {
    namespaceId: string;
    serviceName: string;
    groupName: string;
    clusterName?: string;
    pageNo?: number;
    pageSize?: number;
  }): ApiResult<InstanceListResponse> =>
    client.get('v3/console/ns/instance/list', { params }) as ApiResult<InstanceListResponse>,

  // Update instance
  updateInstance: (data: InstanceUpdateData): ApiResult<string> =>
    client.put('v3/console/ns/instance', data) as ApiResult<string>,

  // Delete instance
  deleteInstance: (params: InstanceDeleteData): ApiResult<string> =>
    client.delete('v3/console/ns/instance', { params }) as ApiResult<string>,

  // List subscribers
  listSubscribers: (params: {
    namespaceId: string;
    serviceName: string;
    groupName?: string;
    pageNo?: number;
    pageSize?: number;
    aggregation?: boolean;
  }): ApiResult<SubscriberListResponse> =>
    client.get('v3/console/ns/service/subscribers', { params }) as ApiResult<SubscriberListResponse>,
};
