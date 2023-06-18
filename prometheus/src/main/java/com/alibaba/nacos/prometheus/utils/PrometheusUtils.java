package com.alibaba.nacos.prometheus.utils;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;

public class PrometheusUtils {

    public static void assembleArrayNodes(Set<Instance> targetSet, ArrayNode arrayNode) {
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
    }
}
