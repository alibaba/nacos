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

package com.alibaba.nacos.maintainer.client.utils;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request Utils.
 *
 * @author xiweng.yy
 */
public class RequestUtil {
    
    /**
     * Transfer {@link Service} to HTTP API request parameters.
     *
     * @param service {@link Service} object
     * @return HTTP API request parameters
     */
    public static Map<String, String> toParameters(Service service) {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", service.getNamespaceId());
        params.put("groupName", service.getGroupName());
        params.put("serviceName", service.getName());
        params.put("metadata", JacksonUtils.toJson(service.getMetadata()));
        params.put("ephemeral", String.valueOf(service.isEphemeral()));
        params.put("protectThreshold", String.valueOf(service.getProtectThreshold()));
        params.put("selector", JacksonUtils.toJson(service.getSelector()));
        return params;
    }
    
    /**
     * Transfer {@link Service} and {@link Instance} to HTTP API request parameters.
     *
     * @param service {@link Service} object
     * @param instance {@link Instance} object
     * @return HTTP API request parameters
     */
    public static Map<String, String> toParameters(Service service, Instance instance) {
        Map<String, String> params = new HashMap<>(11);
        params.put("namespaceId", service.getNamespaceId());
        params.put("groupName", service.getGroupName());
        params.put("serviceName", service.getName());
        params.put("clusterName", instance.getClusterName());
        params.put("ip", instance.getIp());
        params.put("port", String.valueOf(instance.getPort()));
        params.put("weight", String.valueOf(instance.getWeight()));
        params.put("healthy", String.valueOf(instance.isHealthy()));
        params.put("enabled", String.valueOf(instance.isEnabled()));
        params.put("metadata", JacksonUtils.toJson(instance.getMetadata()));
        params.put("ephemeral", String.valueOf(instance.isEphemeral()));
        return params;
    }
    
    /**
     * Transfer {@link Service}, list of {@link Instance} and new Metadata map to HTTP API request parameters.
     *
     * @param service {@link Service} object
     * @param instances list of  {@link Instance}
     * @param newMetadata new Metadata map
     * @return HTTP API request parameters
     */
    public static Map<String, String> toParameters(Service service, List<Instance> instances,
            Map<String, String> newMetadata) {
        Map<String, String> params = new HashMap<>(6);
        params.put("namespaceId", service.getNamespaceId());
        params.put("groupName", service.getGroupName());
        params.put("serviceName", service.getName());
        params.put("instances", JacksonUtils.toJson(instances));
        params.put("consistencyType", instances.get(0).isEphemeral() ? "ephemeral" : "persist");
        params.put("metadata", JacksonUtils.toJson(newMetadata));
        return params;
    }
    
    /**
     * Transfer {@link Service}, list of {@link ClusterInfo} to HTTP API request parameters.
     *
     * @param service {@link Service} object
     * @param cluster list of  {@link ClusterInfo}
     * @return HTTP API request parameters
     */
    public static Map<String, String> toParameters(Service service, ClusterInfo cluster) {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", service.getNamespaceId());
        params.put("groupName", service.getGroupName());
        params.put("serviceName", service.getName());
        params.put("clusterName", cluster.getClusterName());
        params.put("checkPort", String.valueOf(cluster.getHealthyCheckPort()));
        params.put("useInstancePort4Check", String.valueOf(cluster.isUseInstancePortForCheck()));
        params.put("healthChecker", JacksonUtils.toJson(cluster.getHealthChecker()));
        params.put("metadata", JacksonUtils.toJson(cluster.getMetadata()));
        return params;
    }
}
