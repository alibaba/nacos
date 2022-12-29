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

package com.alibaba.nacos.prometheus.controller;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.prometheus.api.ApiConstants;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;

/**
 * Support Prometheus SD Controller.
 *
 * @author karsonto
 */
@RestController
@ConditionalOnProperty(name = "nacos.prometheus.metrics.enabled", havingValue = "true")
public class PrometheusController {
    
    @Autowired
    private InstanceOperatorClientImpl instanceServiceV2;
    
    private final ServiceManager serviceManager;
    
    public PrometheusController() {
        this.serviceManager = ServiceManager.getInstance();
    }
    
    /**
     * Get all service instances.
     *
     * @throws NacosException NacosException.
     */
    @GetMapping(value = ApiConstants.PROMETHEUS_CONTROLLER_PATH, produces = "application/json; charset=UTF-8")
    public ResponseEntity metric() throws NacosException {
        ArrayNode arrayNode = JacksonUtils.createEmptyArrayNode();
        Set<Instance> targetSet = new HashSet<>();
        Set<String> allNamespaces = serviceManager.getAllNamespaces();
        for (String namespace : allNamespaces) {
            Set<Service> singletons = serviceManager.getSingletons(namespace);
            for (Service service : singletons) {
                
                List<? extends Instance> instances = instanceServiceV2.listAllInstances(namespace,
                        service.getGroupedServiceName());
                
                for (Instance instance : instances) {
                    targetSet.add(instance);
                }
                
            }
        }
        Map<String, List<Instance>> groupingInsMap = targetSet.stream().collect(groupingBy(Instance::getClusterName));
        groupingInsMap.forEach((key, value) -> {
            ObjectNode jsonNode = JacksonUtils.createEmptyJsonNode();
            ArrayNode targetsNode = JacksonUtils.createEmptyArrayNode();
            ObjectNode labelNode = JacksonUtils.createEmptyJsonNode();
            value.forEach(e -> {
                targetsNode.add(e.getIp() + ":" + e.getPort());
            });
            labelNode.put("__meta_clusterName", key);
            jsonNode.replace("targets", targetsNode);
            jsonNode.replace("labels", labelNode);
            arrayNode.add(jsonNode);
            
        });
        return ResponseEntity.ok().body(arrayNode.toString());
    }
}
