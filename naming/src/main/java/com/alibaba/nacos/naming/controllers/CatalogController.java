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

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.CatalogService;
import com.alibaba.nacos.naming.core.CatalogServiceV1Impl;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.healthcheck.HealthCheckTask;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Catalog controller.
 *
 * @author nkorange
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_CATALOG_CONTEXT)
public class CatalogController {
    
    @Autowired
    protected ServiceManager serviceManager;
    
    @Autowired
    private CatalogServiceV1Impl catalogServiceV1;
    
    @Autowired
    private CatalogServiceV2Impl catalogServiceV2;
    
    @Autowired
    private UpgradeJudgement upgradeJudgement;
    
    /**
     * Get service detail.
     *
     * @param namespaceId namespace id
     * @param serviceName service name
     * @return service detail information
     * @throws NacosException nacos exception
     */
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    @GetMapping("/service")
    public Object serviceDetail(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            String serviceName) throws NacosException {
        String serviceNameWithoutGroup = NamingUtils.getServiceName(serviceName);
        String groupName = NamingUtils.getGroupName(serviceName);
        return judgeCatalogService().getServiceDetail(namespaceId, groupName, serviceNameWithoutGroup);
    }
    
    /**
     * List instances of special service.
     *
     * @param namespaceId namespace id
     * @param serviceName service name
     * @param clusterName cluster name
     * @param page        number of page
     * @param pageSize    size of each page
     * @return instances information
     * @throws NacosException nacos exception
     */
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    @RequestMapping(value = "/instances")
    public ObjectNode instanceList(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam String clusterName, @RequestParam(name = "pageNo") int page,
            @RequestParam int pageSize) throws NacosException {
        String serviceNameWithoutGroup = NamingUtils.getServiceName(serviceName);
        String groupName = NamingUtils.getGroupName(serviceName);
        List<? extends Instance> instances = judgeCatalogService()
                .listInstances(namespaceId, groupName, serviceNameWithoutGroup, clusterName);
        int start = (page - 1) * pageSize;
        int end = page * pageSize;
        
        if (start < 0) {
            start = 0;
        }
        
        if (start > instances.size()) {
            start = instances.size();
        }
        
        if (end > instances.size()) {
            end = instances.size();
        }
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.replace("list", JacksonUtils.transferToJsonNode(instances.subList(start, end)));
        result.put("count", instances.size());
        
        return result;
    }
    
    /**
     * List service detail information.
     *
     * @param withInstances     whether return instances
     * @param namespaceId       namespace id
     * @param pageNo            number of page
     * @param pageSize          size of each page
     * @param serviceName       service name
     * @param groupName         group name
     * @param containedInstance instance name pattern which will be contained in detail
     * @param hasIpCount        whether filter services with empty instance
     * @return list service detail
     */
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    @GetMapping("/services")
    public Object listDetail(@RequestParam(required = false) boolean withInstances,
            @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam(required = false) int pageNo, @RequestParam(required = false) int pageSize,
            @RequestParam(name = "serviceNameParam", defaultValue = StringUtils.EMPTY) String serviceName,
            @RequestParam(name = "groupNameParam", defaultValue = StringUtils.EMPTY) String groupName,
            @RequestParam(name = "instance", defaultValue = StringUtils.EMPTY) String containedInstance,
            @RequestParam(required = false) boolean hasIpCount) throws NacosException {
        
        if (withInstances) {
            return judgeCatalogService().pageListServiceDetail(namespaceId, groupName, serviceName, pageNo, pageSize);
        }
        return judgeCatalogService()
                .pageListService(namespaceId, groupName, serviceName, pageNo, pageSize, containedInstance, hasIpCount);
    }
    
    /**
     * Get response time of service.
     *
     * @param request http request
     * @return response time information
     */
    @RequestMapping("/rt/service")
    public ObjectNode rt4Service(HttpServletRequest request) throws NacosException {
        
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        
        Service service = serviceManager.getService(namespaceId, serviceName);
        
        serviceManager.checkServiceIsNull(service, namespaceId, serviceName);
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        
        ArrayNode clusters = JacksonUtils.createEmptyArrayNode();
        for (Map.Entry<String, com.alibaba.nacos.naming.core.Cluster> entry : service.getClusterMap().entrySet()) {
            ObjectNode packet = JacksonUtils.createEmptyJsonNode();
            HealthCheckTask task = entry.getValue().getHealthCheckTask();
            
            packet.put("name", entry.getKey());
            packet.put("checkRTBest", task.getCheckRtBest());
            packet.put("checkRTWorst", task.getCheckRtWorst());
            packet.put("checkRTNormalized", task.getCheckRtNormalized());
            
            clusters.add(packet);
        }
        result.replace("clusters", clusters);
        return result;
    }
    
    private CatalogService judgeCatalogService() {
        return upgradeJudgement.isUseGrpcFeatures() ? catalogServiceV2 : catalogServiceV1;
    }
}
