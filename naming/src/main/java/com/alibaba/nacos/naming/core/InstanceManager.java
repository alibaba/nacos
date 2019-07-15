/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * @author jifengnan 2019-06-14
 */
@Component
@DependsOn("nacosApplicationContext")
public class InstanceManager {
    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private SwitchDomain switchDomain;

    /**
     * Register an instance to a service in AP mode.
     * <p>
     * This method creates service or cluster silently if they don't exist.
     *
     * @param request the current HTTP request
     * @throws Exception if the server for the instance is not found
     */
    public Instance register(HttpServletRequest request) throws Exception {
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        boolean ephemeral = BooleanUtils.toBoolean(WebUtils.optional(request, "ephemeral",
            String.valueOf(switchDomain.isDefaultInstanceEphemeral())));
        Service service = serviceManager.createServiceIfAbsent(namespaceId, serviceName, ephemeral);

        if (service == null) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "service not found, namespace: " + namespaceId + ", service: " + serviceName);
        }

        Instance instance = grabInstance(request, service);
        serviceManager.addInstance(namespaceId, serviceName, instance.isEphemeral(), instance);
        return instance;
    }

    /**
     * Update the instance.
     *
     * @param request the current HTTP request
     * @throws Exception if the server for the instance is not found
     */
    public Instance update(HttpServletRequest request) throws Exception {
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);

        Service service = serviceManager.getService(namespaceId, serviceName);

        if (service == null) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "service not found, namespace: " + namespaceId + ", service: " + serviceName);
        }
        Instance instance = grabInstance(request, service);

        if (!service.allIPs().contains(instance)) {
            throw new NacosException(NacosException.INVALID_PARAM, "instance not exist: " + instance);
        }

        serviceManager.addInstance(namespaceId, serviceName, instance.isEphemeral(), instance);
        return instance;
    }

    /**
     * Deregister the instance.
     *
     * @param request the current http request
     * @throws Exception if the server for the instance is not found
     */
    public Instance deregister(HttpServletRequest request) throws Exception {
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);

        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            Loggers.SRV_LOG.warn("remove instance from non-exist service: {}", serviceName);
            return null;
        }

        Instance instance = grabInstance(request, service);

        serviceManager.removeInstance(namespaceId, serviceName, instance.isEphemeral(), instance);
        return instance;
    }

    /**
     * Grab instance from the HTTP request.
     *
     * @param request the current HTTP request
     * @param service the service to which the new instance belongs
     * @return the new instance
     * @throws Exception if metadata format incorrect
     */
    private Instance grabInstance(HttpServletRequest request, Service service) throws Exception {
        String ip = WebUtils.required(request, "ip");
        String port = WebUtils.required(request, "port");
        String weight = WebUtils.optional(request, "weight", "1");
        String clusterName = WebUtils.optional(request, CommonParams.CLUSTER_NAME, StringUtils.EMPTY);
        if (StringUtils.isBlank(clusterName)) {
            clusterName = WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        }
        boolean healthy = BooleanUtils.toBoolean(WebUtils.optional(request, "healthy", "true"));
        String enabledString = WebUtils.optional(request, "enabled", StringUtils.EMPTY);
        boolean enabled;
        if (StringUtils.isBlank(enabledString)) {
            enabled = BooleanUtils.toBoolean(WebUtils.optional(request, "enable", "true"));
        } else {
            enabled = BooleanUtils.toBoolean(enabledString);
        }

        boolean ephemeral = BooleanUtils.toBoolean(WebUtils.optional(request, "ephemeral",
            String.valueOf(switchDomain.isDefaultInstanceEphemeral())));
        Cluster cluster = service.getClusterMap().get(clusterName);
        if (cluster == null) {
            cluster = new Cluster(clusterName, service);
        }
        Instance instance = new Instance(ip, Integer.parseInt(port), cluster);
        instance.setWeight(Double.parseDouble(weight));
        instance.setHealthy(healthy);
        instance.setEnabled(enabled);
        instance.setEphemeral(ephemeral);
        String app = WebUtils.optional(request, "app", "DEFAULT");
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
        instance.setApp(app);
        instance.setInstanceId(instance.generateInstanceId());
        instance.setLastBeat(System.currentTimeMillis());
        if (StringUtils.isNotEmpty(metadata)) {
            instance.setMetadata(UtilsAndCommons.parseMetadata(metadata));
        }
        return instance;
    }
}
