import client from './client';
import type { AxiosPromise } from 'axios';
import type {
  ServiceListParams,
  ServiceListResponse,
  ServiceDetailInfo,
  ServiceFormData,
  ClusterUpdateData,
  InstanceListResponse,
  InstanceUpdateData,
  SubscriberListResponse,
} from '@/types/service';

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export const serviceApi = {
  // List services
  listServices: (params: ServiceListParams): AxiosPromise<ApiResponse<ServiceListResponse>> =>
    client.get('v3/console/ns/service/list', { params }),

  // Get service detail
  getService: (params: { namespaceId: string; serviceName: string; groupName: string }): AxiosPromise<ApiResponse<ServiceDetailInfo>> =>
    client.get('v3/console/ns/service', { params }),

  // Create service
  createService: (data: ServiceFormData): AxiosPromise<ApiResponse<string>> =>
    client.post('v3/console/ns/service', data),

  // Update service
  updateService: (data: ServiceFormData): AxiosPromise<ApiResponse<string>> =>
    client.put('v3/console/ns/service', data),

  // Delete service
  deleteService: (params: { namespaceId: string; serviceName: string; groupName: string }): AxiosPromise<ApiResponse<string>> =>
    client.delete('v3/console/ns/service', { params }),

  // Get selector types
  getSelectorTypes: (): AxiosPromise<ApiResponse<string[]>> =>
    client.get('v3/console/ns/service/selector/types'),

  // Update cluster
  updateCluster: (data: ClusterUpdateData): AxiosPromise<ApiResponse<string>> =>
    client.put('v3/console/ns/service/cluster', data),

  // List instances
  listInstances: (params: {
    namespaceId: string;
    serviceName: string;
    groupName: string;
    clusterName?: string;
    pageNo?: number;
    pageSize?: number;
  }): AxiosPromise<ApiResponse<InstanceListResponse>> =>
    client.get('v3/console/ns/instance/list', { params }),

  // Update instance
  updateInstance: (data: InstanceUpdateData): AxiosPromise<ApiResponse<string>> =>
    client.put('v3/console/ns/instance', data),

  // List subscribers
  listSubscribers: (params: {
    namespaceId: string;
    serviceName: string;
    groupName?: string;
    pageNo?: number;
    pageSize?: number;
    aggregation?: boolean;
  }): AxiosPromise<ApiResponse<SubscriberListResponse>> =>
    client.get('v3/console/ns/service/subscribers', { params }),
};
