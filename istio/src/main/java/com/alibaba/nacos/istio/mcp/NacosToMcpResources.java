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

package com.alibaba.nacos.istio.mcp;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import istio.mcp.v1alpha1.MetadataOuterClass;
import istio.mcp.v1alpha1.ResourceOuterClass;
import istio.networking.v1alpha3.GatewayOuterClass;
import istio.networking.v1alpha3.ServiceEntryOuterClass;
import istio.networking.v1alpha3.WorkloadEntryOuterClass;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos MCP server.
 *
 * <p>This MCP serves as a ResourceSource defined by Istio.
 *
 * @author huaicheng.lzp
 * @since 1.2.1
 */
@org.springframework.stereotype.Service
public class NacosToMcpResources {
    
    private final Map<String, ResourceOuterClass.Resource> resourceMap = new ConcurrentHashMap<>(16);
    
    private final Map<String, String> checksumMap = new ConcurrentHashMap<>(16);
    
    private static final String SERVICE_NAME_SPLITTER = "nacos";
    
    private static final String MESSAGE_TYPE_URL = "type.googleapis.com/istio.networking.v1alpha3.ServiceEntry";
    
    private static final long MCP_PUSH_PERIOD_MILLISECONDS = 10000L;
    
    @Autowired
    private NacosMcpOverXdsService nacosMcpOverXdsService;
    
    @Autowired
    private NacosMcpService nacosMcpService;
    
    @Autowired
    private ServiceManager serviceManager;
    
    public void start() {
        GlobalExecutor
                .scheduleMcpPushTask(new McpPushTask(), MCP_PUSH_PERIOD_MILLISECONDS * 2, MCP_PUSH_PERIOD_MILLISECONDS);
    }
    
    private class McpPushTask implements Runnable {
        
        @Override
        public void run() {
            
            boolean changed = false;
            
            // Query all services to see if any of them have changes:
            Set<String> namespaces = serviceManager.getAllNamespaces();
            Set<String> allServices = new HashSet<>();
            for (String namespace : namespaces) {
                
                Map<String, Service> services = serviceManager.chooseServiceMap(namespace);
                
                if (services.isEmpty()) {
                    continue;
                }
                
                for (Service service : services.values()) {
                    
                    String convertedName = convertName(service);
                    allServices.add(convertedName);
                    // Service not changed:
                    if (checksumMap.containsKey(convertedName) && checksumMap.get(convertedName)
                            .equals(service.getChecksum())) {
                        continue;
                    }
                    // Update the resource:
                    changed = true;
                    if (service.allIPs().isEmpty()) {
                        resourceMap.remove(convertedName);
                        checksumMap.remove(convertedName);
                        continue;
                    }
                    
                    resourceMap.put(convertedName, convertService(service));
                    checksumMap.put(convertedName, service.getChecksum());
                }
            }
            
            for (String key : resourceMap.keySet()) {
                if (!allServices.contains(key)) {
                    changed = true;
                    resourceMap.remove(key);
                    checksumMap.remove(key);
                }
            }
            if (!changed) {
                // If no service changed, just return:
                return;
            }
            
            nacosMcpOverXdsService.sendResources(resourceMap);
            nacosMcpService.sendResources(resourceMap);
            
        }
    }
    
    private String convertName(Service service) {
        if (!Constants.DEFAULT_GROUP.equals(NamingUtils.getGroupName(service.getName()))) {
            return NamingUtils.getServiceName(service.getName()) + "." + NamingUtils.getGroupName(service.getName())
                    + "." + service.getNamespaceId();
        }
        //DEFAULT_GROUP is invalid for istio,because the istio host only supports: [0-9],[A-Z],[a-z],-,*
        return NamingUtils.getServiceName(service.getName()) + ".DEFAULT-GROUP" + "." + service.getNamespaceId();
    }
    
    private ResourceOuterClass.Resource convertService(Service service) {
        
        String serviceName = convertName(service);
        ServiceEntryOuterClass.ServiceEntry.Builder serviceEntryBuilder = ServiceEntryOuterClass.ServiceEntry
                .newBuilder().setResolution(ServiceEntryOuterClass.ServiceEntry.Resolution.STATIC)
                .setLocation(ServiceEntryOuterClass.ServiceEntry.Location.MESH_INTERNAL)
                .addHosts(serviceName + "." + SERVICE_NAME_SPLITTER);
        int port = 0;
        for (Instance instance : service.allIPs()) {
            if (port == 0) {
                port = instance.getPort();
            }
            if (!instance.isHealthy() || !instance.isEnabled()) {
                continue;
            }
            Map<String, String> metadata = new HashMap<>(instance.getMetadata());
            if (StringUtils.isNotEmpty(instance.getApp())) {
                metadata.put("app", instance.getApp());
            }
            if (StringUtils.isNotEmpty(instance.getTenant())) {
                metadata.put("tenant", instance.getTenant());
            }
            if (StringUtils.isNotEmpty(instance.getClusterName())) {
                metadata.put("cluster", instance.getClusterName());
            }
            WorkloadEntryOuterClass.WorkloadEntry workloadEntry = WorkloadEntryOuterClass.WorkloadEntry.newBuilder()
                    .setAddress(instance.getIp()).setWeight((int) instance.getWeight()).putAllLabels(metadata)
                    .putPorts("http", instance.getPort()).build();
            
            serviceEntryBuilder.addEndpoints(workloadEntry);
        }
        
        serviceEntryBuilder.addPorts(
                GatewayOuterClass.Port.newBuilder().setNumber(port).setName("http").setProtocol("HTTP").build());
        
        ServiceEntryOuterClass.ServiceEntry serviceEntry = serviceEntryBuilder.build();
        
        Any any = Any.newBuilder().setValue(serviceEntry.toByteString()).setTypeUrl(MESSAGE_TYPE_URL).build();
        MetadataOuterClass.Metadata metadata = MetadataOuterClass.Metadata.newBuilder()
                .setName(SERVICE_NAME_SPLITTER + "/" + serviceName).putAllAnnotations(service.getMetadata())
                .putAnnotations("virtual", "1")
                .setCreateTime(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build())
                .setVersion(service.getChecksum()).build();
        
        return ResourceOuterClass.Resource.newBuilder().setBody(any).setMetadata(metadata).build();
        
    }
}
