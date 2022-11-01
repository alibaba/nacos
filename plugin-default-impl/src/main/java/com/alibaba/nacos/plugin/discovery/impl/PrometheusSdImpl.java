/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.discovery.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.InstanceOperator;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.plugin.discovery.spi.HttpPluginService;
import com.alibaba.nacos.plugin.discovery.wrapper.HttpServletWrapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;

/**
 * Prometheus http service discovery implementation.
 *
 * @author karsonto
 */
public class PrometheusSdImpl implements HttpPluginService {
    
    private static final String ENABLE = "nacos.plugin.http.prometheus.enable";
    
    private static final String URI = "nacos.plugin.http.prometheus.uri";
    
    ServiceManager serviceManager;
    
    InstanceOperator instanceOperator;
    
    HttpServletWrapper servletWrapper;
    
    @Override
    public String getRequestUri() {
        return EnvUtil.getProperty(URI, "/prometheus/*");
    }
    
    @Override
    public void init(ApplicationContext applicationContext) {
        serviceManager = ServiceManager.getInstance();
        instanceOperator = (InstanceOperator) applicationContext.getBean("instanceOperatorClientImpl");
    }
    
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final PrintWriter out = resp.getWriter();
        ArrayNode arrayNode = JacksonUtils.createEmptyArrayNode();
        Set<Instance> targetSet = new HashSet<>();
        Set<String> allNamespaces = serviceManager.getAllNamespaces();
        for (String namespace : allNamespaces) {
            Set<Service> singletons = serviceManager.getSingletons(namespace);
            for (Service service : singletons) {
                try {
                    List<? extends Instance> instances = instanceOperator.listAllInstances(namespace,
                            service.getName());
                    for (Instance instance : instances) {
                        targetSet.add(instance);
                    }
                    
                } catch (NacosException e) {
                    e.printStackTrace();
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
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        out.print(arrayNode.toString());
        out.flush();
    }
    
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(405);
    }
    
    @Override
    public void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(405);
    }
    
    @Override
    public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(405);
    }
    
    @Override
    public boolean enable() {
        return Boolean.parseBoolean(EnvUtil.getProperty(ENABLE, "false"));
    }
    
    @Override
    public void bind(HttpServletWrapper servletWrapper) {
        this.servletWrapper = servletWrapper;
    }
}
