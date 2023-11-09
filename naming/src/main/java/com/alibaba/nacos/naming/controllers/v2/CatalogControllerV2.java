/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CatalogControllerV2.
 *
 * @author Weizhan▪Yun
 * @date 2023/1/14 19:54
 */
@RestController
@RequestMapping(UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_CATALOG_CONTEXT)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
public class CatalogControllerV2 {
    
    @Autowired
    private CatalogServiceV2Impl catalogServiceV2;
    
    /**
     * List instances of special service.
     *
     * @param namespaceId namespace id
     * @param serviceName service name
     * @param healthyOnly instance health only
     * @param enabledOnly instance enabled
     * @param page        number of page
     * @param pageSize    size of each page
     * @return instances information
     */
    @Secured(action = ActionTypes.READ)
    @RequestMapping(value = "/instances")
    public Result<ObjectNode> instanceList(
            @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam(required = false) Boolean healthyOnly,
            @RequestParam(required = false) Boolean enabledOnly, @RequestParam(name = "pageNo") int page,
            @RequestParam int pageSize) {
        String serviceNameWithoutGroup = NamingUtils.getServiceName(serviceName);
        String groupName = NamingUtils.getGroupName(serviceName);
        List<? extends Instance> instances = catalogServiceV2.listAllInstances(namespaceId, groupName,
                serviceNameWithoutGroup);
        int start = (page - 1) * pageSize;
        
        if (start < 0) {
            start = 0;
        }
        int end = start + pageSize;
        
        if (start > instances.size()) {
            start = instances.size();
        }
        
        if (end > instances.size()) {
            end = instances.size();
        }
        
        Stream<? extends Instance> stream = instances.stream();
        if (healthyOnly != null) {
            stream = stream.filter(instance -> instance.isHealthy() == healthyOnly);
        }
        if (enabledOnly != null) {
            stream = stream.filter(i -> i.isEnabled() == enabledOnly);
        }
        List<? extends Instance> ins = stream.collect(Collectors.toList());
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        if (ins.size() > start) {
            result.replace("instances", JacksonUtils.transferToJsonNode(ins.subList(start, end)));
        }
        result.put("count", ins.size());
        
        return Result.success(result);
    }
}
