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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.CatalogService;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalog controller.
 *
 * @author nkorange
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_CATALOG_CONTEXT)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
public class CatalogController {
    
    @Autowired
    private CatalogServiceV2Impl catalogServiceV2;
    
    /**
     * Get service detail.
     *
     * @param namespaceId namespace id
     * @param serviceName service name
     * @return service detail information
     * @throws NacosException nacos exception
     */
    @Secured(action = ActionTypes.READ)
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
    @Secured(action = ActionTypes.READ)
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
    @Secured(action = ActionTypes.READ)
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
    
    private CatalogService judgeCatalogService() {
        return catalogServiceV2;
    }
}
