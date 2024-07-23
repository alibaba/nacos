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

package com.alibaba.nacos.prometheus.utils;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * prometheus common utils.
 *
 * @author Joey777210
 */
public class PrometheusUtils {
    
    /**
     * Assemble arrayNodes for prometheus sd api.
     */
    public static void assembleArrayNodes(Set<Instance> targetSet, ArrayNode arrayNode) {
        Map<String, List<Instance>> groupingInsMap = targetSet.stream().collect(groupingBy(Instance::getClusterName));
        groupingInsMap.forEach((key, value) -> {
            for (Instance instance : value) {
                ObjectNode jsonNode = assembleInstanceToArrayNode(key, instance);
                arrayNode.add(jsonNode);
            }
        });
    }
    
    /**
     * assemble instance to json node, and export metadata to label.
     *
     * @param clusterName the cluster name
     * @param instance    instance info
     */
    private static ObjectNode assembleInstanceToArrayNode(String clusterName, Instance instance) {
        
        ArrayNode targetsNode = JacksonUtils.createEmptyArrayNode();
        targetsNode.add(instance.getIp() + ":" + instance.getPort());
        ObjectNode labelNode = JacksonUtils.createEmptyJsonNode();
        //mark cluster name
        labelNode.put("__meta_clusterName", clusterName);
        //export metadata
        Map<String, String> metadata = instance.getMetadata();
        // auto convert label names contain with "." and "-" to "_"
        metadata = metadata.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().replace(".", "_").replace("-", "_"), e -> e.getValue()));
        
        metadata.forEach(labelNode::put);
        ObjectNode jsonNode = JacksonUtils.createEmptyJsonNode();
        jsonNode.replace("targets", targetsNode);
        jsonNode.replace("labels", labelNode);
        return jsonNode;
    }
}
