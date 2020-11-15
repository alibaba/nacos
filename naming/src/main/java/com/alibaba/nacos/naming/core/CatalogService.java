/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;

import java.util.List;

/**
 * Catalog service.
 *
 * @author xiweng.yy
 */
public interface CatalogService {
    
    /**
     * Get service detail information.
     *
     * @param namespaceId namespace id of service
     * @param groupName   group name of service
     * @param serviceName service name
     * @return detail information of service
     * @throws NacosException exception in query
     */
    Object getServiceDetail(String namespaceId, String groupName, String serviceName) throws NacosException;
    
    /**
     * List all instances of specified services.
     *
     * @param namespaceId namespace id of service
     * @param groupName   group name of service
     * @param serviceName service name
     * @param clusterName cluster name of instances
     * @return instances list
     * @throws NacosException exception in query
     */
    List<? extends Instance> listInstances(String namespaceId, String groupName, String serviceName, String clusterName)
            throws NacosException;
    
    /**
     * List service by page.
     *
     * @param namespaceId        namespace id of service
     * @param groupName          group name of service
     * @param serviceName        service name
     * @param pageNo             page number
     * @param pageSize           page size
     * @param instancePattern    contained instances pattern
     * @param ignoreEmptyService whether ignore empty service
     * @return service list
     * @throws NacosException exception in query
     */
    Object pageListService(String namespaceId, String groupName, String serviceName, int pageNo, int pageSize,
            String instancePattern, boolean ignoreEmptyService) throws NacosException;
    
    /**
     * List service with cluster and instances by page.
     *
     * @param namespaceId namespace id of service
     * @param groupName   group name of service
     * @param serviceName service name
     * @param pageNo      page number
     * @param pageSize    page size
     * @return service list
     * @throws NacosException exception in query
     */
    Object pageListServiceDetail(String namespaceId, String groupName, String serviceName, int pageNo, int pageSize)
            throws NacosException;
}
