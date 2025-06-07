/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.naming;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;

import java.util.List;
import java.util.Map;

/**
 * Nacos naming module service maintainer API.
 *
 * @author xiweng.yy
 */
public interface ServiceMaintainerService {
    
    /**
     * Create a new service with the given service name, default namespaceId and groupName.
     *
     * <p>Created service is persistent, no protectThreshold and no selector.</p>
     *
     * @param serviceName the name of the service
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String createService(String serviceName) throws NacosException {
        return createService(Constants.DEFAULT_GROUP, serviceName);
    }
    
    /**
     * Create a new service with the given group name, service name and default namespaceId.
     *
     * <p>Created service is persistent, no protectThreshold and no selector.</p>
     *
     * @param groupName    the group name
     * @param serviceName  the name of the service
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String createService(String groupName, String serviceName) throws NacosException {
        return createService(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName);
    }
    
    /**
     * Create a new service with the given namespaceId, group name and service name.
     *
     * <p>Created service is persistent, no protectThreshold and no selector.</p>
     *
     * @param namespaceId  the namespace id of the service
     * @param groupName    the group name of service
     * @param serviceName  the name of the service
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String createService(String namespaceId, String groupName, String serviceName) throws NacosException {
        return createService(namespaceId, groupName, serviceName, false);
    }
    
    /**
     * Create a new service with the given namespaceId, group name and service name.
     *
     * <p>Created service is no protectThreshold and no selector.</p>
     *
     * @param namespaceId  the namespace id of the service
     * @param groupName    the group name of service
     * @param serviceName  the name of the service
     * @param ephemeral    if {@code true}, the service is ephemeral, otherwise persistent.
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String createService(String namespaceId, String groupName, String serviceName, boolean ephemeral)
            throws NacosException {
        return createService(namespaceId, groupName, serviceName, ephemeral, 0.0F);
    }
    
    /**
     * Create a new service with the given namespaceId, group name and service name.
     *
     * <p>Created service is no selector.</p>
     *
     * @param namespaceId      the namespace id of the service
     * @param groupName        the group name of service
     * @param serviceName      the name of the service
     * @param ephemeral        if {@code true}, the service is ephemeral, otherwise persistent.
     * @param protectThreshold the protect threshold of the service
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String createService(String namespaceId, String groupName, String serviceName, boolean ephemeral,
            float protectThreshold) throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        service.setEphemeral(ephemeral);
        service.setProtectThreshold(protectThreshold);
        return createService(service);
    }
    
    /**
     * Create a new service with detailed parameters.
     *
     * @param service   {@link Service} full information of service to be created.
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String createService(Service service) throws NacosException;
    
    /**
     * Update an existing persistent service for default namespace id and default group name.
     *
     * @param serviceName           the name of the updated service
     * @param newMetadata           the new metadata of the updated service, will replace all existing metadata.
     * @param newProtectThreshold   the new protect threshold of the updated service
     * @param newSelector           the new selector of the updated service
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String updateService(String serviceName, Map<String, String> newMetadata, float newProtectThreshold,
            Selector newSelector) throws NacosException {
        return updateService(Constants.DEFAULT_GROUP, serviceName, newMetadata, newProtectThreshold, newSelector);
    }
    
    /**
     * Update an existing persistent service for default namespace id.
     *
     * @param groupName             the group name of the updated service
     * @param serviceName           the name of the updated service
     * @param newMetadata           the new metadata of the updated service, will replace all existing metadata.
     * @param newProtectThreshold   the new protect threshold of the updated service
     * @param newSelector           the new selector of the updated service
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String updateService(String groupName, String serviceName, Map<String, String> newMetadata,
            float newProtectThreshold, Selector newSelector) throws NacosException {
        return updateService(ParamUtil.getDefaultNamespaceId(), groupName, serviceName, newMetadata,
                newProtectThreshold, newSelector);
    }
    
    /**
     * Update an existing persistent service.
     *
     * @param namespaceId           the namespace id of the updated service
     * @param groupName             the group name of the updated service
     * @param serviceName           the name of the updated service
     * @param newMetadata           the new metadata of the updated service, will replace all existing metadata.
     * @param newProtectThreshold   the new protect threshold of the updated service
     * @param newSelector           the new selector of the updated service
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String updateService(String namespaceId, String groupName, String serviceName,
            Map<String, String> newMetadata, float newProtectThreshold, Selector newSelector) throws NacosException {
        return updateService(namespaceId, groupName, serviceName, false, newMetadata, newProtectThreshold, newSelector);
    }
    
    /**
     * Update an existing service.
     *
     * @param namespaceId           the namespace id of the updated service
     * @param groupName             the group name of the updated service
     * @param serviceName           the name of the updated service
     * @param ephemeral             the ephemeral flag of the updated service
     * @param newMetadata           the new metadata of the updated service, will replace all existing metadata.
     * @param newProtectThreshold   the new protect threshold of the updated service
     * @param newSelector           the new selector of the updated service
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String updateService(String namespaceId, String groupName, String serviceName, boolean ephemeral,
            Map<String, String> newMetadata, float newProtectThreshold, Selector newSelector) throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        service.setEphemeral(ephemeral);
        service.setProtectThreshold(newProtectThreshold);
        service.setMetadata(newMetadata);
        service.setSelector(newSelector);
        return updateService(service);
    }
    
    /**
     * Update an existing service.
     *
     * <p>
     *     the four information {@link Service#getNamespaceId()}, {@link Service#getGroupName()}, {@link Service#getName()}
     *     , {@link Service#isEphemeral()} will be used to identity the service to be updated.
     *     The other information is used to replace the existing information.
     *     The Best way to do update is to use {@link #getServiceDetail(String, String, String)} to get current service first,
     *     then call this method to do update.
     * </p>
     *
     * @param service {@link Service} full information of service to be updated.
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String updateService(Service service) throws NacosException;
    
    /**
     * Remove a service with default namespace id and default group name.
     *
     * <p>ephemeral is not matter, due to one service name can't be ephemeral and persistent at the same time.</p>
     *
     * @param serviceName the service name
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String removeService(String serviceName) throws NacosException {
        return removeService(ParamUtil.getDefaultGroupName(), serviceName);
    }
    
    /**
     * Remove a service with default namespace id.
     *
     * <p>ephemeral is not matter, due to one service name can't be ephemeral and persistent at the same time.</p>
     *
     * @param groupName   the group name
     * @param serviceName the service name
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String removeService(String groupName, String serviceName) throws NacosException {
        return removeService(ParamUtil.getDefaultNamespaceId(), groupName, serviceName);
    }
    
    /**
     * Remove a service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String removeService(String namespaceId, String groupName, String serviceName) throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        return removeService(service);
    }
    
    /**
     * Remove a service.
     *
     * @param service {@link Service} to be removed, need {@link Service#getNamespaceId()}, {@link Service#getGroupName()}, {@link Service#getName()}
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String removeService(Service service) throws NacosException;
    
    /**
     * Get detailed information of a service with default namespace id and default group name.
     *
     * @param serviceName the service name
     * @return the service detail information
     * @throws NacosException if an error occurs
     */
    default ServiceDetailInfo getServiceDetail(String serviceName) throws NacosException {
        return getServiceDetail(Constants.DEFAULT_GROUP, serviceName);
    }
    
    /**
     * Get detailed information of a service with default namespace id.
     *
     * @param groupName   the group name
     * @param serviceName the service name
     * @return the service detail information
     * @throws NacosException if an error occurs
     */
    default ServiceDetailInfo getServiceDetail(String groupName, String serviceName) throws NacosException {
        return getServiceDetail(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName);
    }
    
    /**
     * Get detailed information of a service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @return the service detail information
     * @throws NacosException if an error occurs
     */
    default ServiceDetailInfo getServiceDetail(String namespaceId, String groupName, String serviceName)
            throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        return getServiceDetail(service);
    }
    
    /**
     * Get detailed information of a service.
     *
     * @param service {@link Service} to be get, need {@link Service#getNamespaceId()}, {@link Service#getGroupName()}, {@link Service#getName()}
     * @return the service detail information
     * @throws NacosException if an error occurs
     */
    ServiceDetailInfo getServiceDetail(Service service) throws NacosException;
    
    /**
     * List all services for target namespace id.
     *
     * <p>Only list the first 100 services if this namespace contains more than 100 services</p>
     *
     * @param namespaceId target namespace id
     * @return page of service view, {@link ServiceView} is a summary of service.
     * @throws NacosException if an error occurs
     */
    default Page<ServiceView> listServices(String namespaceId) throws NacosException {
        return listServices(namespaceId, StringUtils.EMPTY, StringUtils.EMPTY);
    }
    
    /**
     * List all services for target namespace id with pattern.
     *
     * <p>Only list the first 100 services if this namespace contains more than 100 services</p>
     *
     * @param namespaceId       target namespace id
     * @param groupNameParam    the group name pattern, e.g., "" for all groups, "group" for all services groupName match `.*group.*`.
     * @param serviceNameParam  the service name pattern, e.g., "" for all services, "service" for all services name match `.*service.*`.
     * @return page of service view, {@link ServiceView} is a summary of service.
     * @throws NacosException if an error occurs
     */
    default Page<ServiceView> listServices(String namespaceId, String groupNameParam, String serviceNameParam)
            throws NacosException {
        return listServices(namespaceId, groupNameParam, serviceNameParam, true, 1, 100);
    }
    
    /**
     * List all services for target namespace id with pattern.
     *
     * <p>If input page info is larger than services count, will return empty list</p>
     *
     * @param namespaceId        target namespace id
     * @param groupNameParam     the group name pattern, e.g., "" for all groups, "group" for all services groupName match `.*group.*`.
     * @param serviceNameParam   the service name pattern, e.g., "" for all services, "service" for all services name match `.*service.*`.
     * @param ignoreEmptyService whether ignore empty service, {@code true} will exclude these services without any instance.
     * @param pageNo             page number, start from 1
     * @param pageSize           page size per page
     * @return page of service view, {@link ServiceView} is a summary of service.
     * @throws NacosException if an error occurs
     */
    Page<ServiceView> listServices(String namespaceId, String groupNameParam, String serviceNameParam,
            boolean ignoreEmptyService, int pageNo, int pageSize) throws NacosException;
    
    /**
     * List all services with detail for target namespace id with pattern.
     *
     * <p>If service is empty, will no include in result </p>
     * <p>Only list the first 100 services if this namespace contains more than 100 services</p>
     * <p>
     *     The API may return a large amount of data, leading to significant network bandwidth and traffic consumption.
     *     Please evaluate whether the network bandwidth and traffic are sufficient before calling.
     * </p>
     *
     * @param namespaceId target namespace id
     * @return page of service detail, {@link ServiceDetailInfo} is a detail info of service.
     * @throws NacosException if an error occurs
     */
    default Page<ServiceDetailInfo> listServicesWithDetail(String namespaceId) throws NacosException {
        return listServicesWithDetail(namespaceId, StringUtils.EMPTY, StringUtils.EMPTY);
    }
    
    /**
     * List all services with detail for target namespace id with pattern.
     *
     * <p>If service is empty, will no include in result </p>
     * <p>Only list the first 100 services if this namespace contains more than 100 services</p>
     * <p>
     *     The API may return a large amount of data, leading to significant network bandwidth and traffic consumption.
     *     Please evaluate whether the network bandwidth and traffic are sufficient before calling.
     * </p>
     *
     * @param namespaceId       target namespace id
     * @param groupNameParam    the group name pattern, e.g., "" for all groups, "group" for all services groupName match `.*group.*`.
     * @param serviceNameParam  the service name pattern, e.g., "" for all services, "service" for all services name match `.*service.*`.
     * @return page of service detail, {@link ServiceDetailInfo} is a detail info of service.
     * @throws NacosException if an error occurs
     */
    default Page<ServiceDetailInfo> listServicesWithDetail(String namespaceId, String groupNameParam,
            String serviceNameParam) throws NacosException {
        return listServicesWithDetail(namespaceId, groupNameParam, serviceNameParam, 1, 100);
    }
    
    /**
     * List all services with detail for target namespace id with pattern.
     *
     * <p>If input page info is larger than services count, will return empty list</p>
     * <p>
     *     The API may return a large amount of data, leading to significant network bandwidth and traffic consumption.
     *     Please evaluate whether the network bandwidth and traffic are sufficient before calling.
     * </p>
     *
     * @param namespaceId           target namespace id
     * @param groupNameParam        the group name pattern, e.g., "" for all groups, "group" for all services groupName match `.*group.*`.
     * @param serviceNameParam      the service name pattern, e.g., "" for all services, "service" for all services name match `.*service.*`.
     * @param pageNo                page number, start from 1
     * @param pageSize              page size per page
     * @return page of service detail, {@link ServiceDetailInfo} is a detail info of service.
     * @throws NacosException if an error occurs
     */
    Page<ServiceDetailInfo> listServicesWithDetail(String namespaceId, String groupNameParam, String serviceNameParam,
            int pageNo, int pageSize) throws NacosException;
    
    /**
     * Get pagination subscribers of a service with default namespace id and default group name.
     *
     * <p>Only list the first 100 service subscribers if this service contains more than 100 subscribers</p>
     * <p>
     *     Default will query subscribers from one nacos server member, is want query from all members,
     *     please use {@link #getSubscribers(Service, int, int, boolean)} with aggregation=true.
     * </p>
     *
     * @param serviceName the service name
     * @return the page of subscribers
     * @throws NacosException if an error occurs
     */
    default Page<SubscriberInfo> getSubscribers(String serviceName) throws NacosException {
        return getSubscribers(Constants.DEFAULT_GROUP, serviceName);
    }
    
    /**
     * Get pagination subscribers of a service with default namespace id.
     *
     * <p>Only list the first 100 service subscribers if this service contains more than 100 subscribers</p>
     * <p>
     *     Default will query subscribers from one nacos server member, is want query from all members,
     *     please use {@link #getSubscribers(Service, int, int, boolean)} with aggregation=true.
     * </p>
     *
     * @param groupName   the group name
     * @param serviceName the service name
     * @return the page of subscribers
     * @throws NacosException if an error occurs
     */
    default Page<SubscriberInfo> getSubscribers(String groupName, String serviceName) throws NacosException {
        return getSubscribers(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName);
    }
    
    /**
     * Get pagination subscribers of a service.
     *
     * <p>Only list the first 100 service subscribers if this service contains more than 100 subscribers</p>
     * <p>
     *     Default will query subscribers from one nacos server member, is want query from all members,
     *     please use {@link #getSubscribers(Service, int, int, boolean)} with aggregation=true.
     * </p>
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @return the page of subscribers
     * @throws NacosException if an error occurs
     */
    default Page<SubscriberInfo> getSubscribers(String namespaceId, String groupName, String serviceName)
            throws NacosException {
        return getSubscribers(namespaceId, groupName, serviceName, 1, 100);
    }
    
    /**
     * Get pagination subscribers of a service.
     *
     * <p>If input page info is larger than subscribers count, will return empty list</p>
     * <p>
     *     Default will query subscribers from one nacos server member, is want query from all members,
     *     please use {@link #getSubscribers(Service, int, int, boolean)} with aggregation=true.
     * </p>
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param pageNo      the page number
     * @param pageSize    the page size
     * @return the page of subscribers
     * @throws NacosException if an error occurs
     */
    default Page<SubscriberInfo> getSubscribers(String namespaceId, String groupName, String serviceName, int pageNo,
            int pageSize) throws NacosException {
        return getSubscribers(namespaceId, groupName, serviceName, pageNo, pageSize, false);
    }
    
    /**
     * Get pagination subscribers of a service.
     *
     * <p>If input page info is larger than subscribers count, will return empty list</p>
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param pageNo      the page number
     * @param pageSize    the page size
     * @param aggregation whether to aggregate results from all server members
     * @return the list of subscribers
     * @throws NacosException if an error occurs
     */
    default Page<SubscriberInfo> getSubscribers(String namespaceId, String groupName, String serviceName, int pageNo,
            int pageSize, boolean aggregation) throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        return getSubscribers(service, pageNo, pageSize, aggregation);
    }
    
    /**
     * Get pagination subscribers of a service.
     *
     * <p>If input page info is larger than subscribers count, will return empty list</p>
     *
     * @param service     {@link Service} to be query, need {@link Service#getNamespaceId()}, {@link Service#getGroupName()},
     *                    {@link Service#getName()}
     * @param pageNo      the page number
     * @param pageSize    the page size
     * @param aggregation whether to aggregate results from all server members
     * @return the list of subscribers
     * @throws NacosException if an error occurs
     */
    Page<SubscriberInfo> getSubscribers(Service service, int pageNo, int pageSize, boolean aggregation)
            throws NacosException;
    
    /**
     * List all selector types.
     *
     * @return the list of selector types
     * @throws NacosException if an error occurs
     */
    List<String> listSelectorTypes() throws NacosException;
    
}
