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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
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
import java.util.List;
import java.util.Optional;
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
    
    private static final String SEPARATOR = ".";
    
    private static final String DEFAULT_SUFFIX = ".DEFAULT-GROUP";
    
    private static final String PORT_PARAM = "http";
    
    private static final String CLUSTER_PARAM = "cluster";
    
    private static final String VIRTUAL_ANNOTATION = "virtual";
    
    private static final String DEFAULT_VIRTUAL = "1";
    
    private static final String HTTP = "HTTP";
    
    @Autowired
    private NacosMcpOverXdsService nacosMcpOverXdsService;
    
    @Autowired
    private NacosMcpService nacosMcpService;
    
    @Autowired
    private ServiceStorage serviceStorage;
    
    @Autowired
    private NamingMetadataManager namingMetadataManager;
    
    public void start() {
        GlobalExecutor
                .scheduleMcpPushTask(new McpPushTask(), MCP_PUSH_PERIOD_MILLISECONDS * 2, MCP_PUSH_PERIOD_MILLISECONDS);
    }
    
    private class McpPushTask implements Runnable {
        
        @Override
        public void run() {
            
            ServiceManager serviceManager = ServiceManager.getInstance();
            
            boolean changed = false;
            
            // Query all services to see if any of them have changes:
            Set<String> namespaces = serviceManager.getAllNamespaces();
            Set<String> allServices = new HashSet<>();
            for (String namespace : namespaces) {
                
                Set<Service> services = serviceManager.getSingletons(namespace);
                
                if (services.isEmpty()) {
                    continue;
                }
                
                for (Service service : services) {
                    ServiceInfo serviceInfo = serviceStorage.getData(service);
                    
                    String convertedName = convertName(service);
                    allServices.add(convertedName);
                    // Service not changed:
                    if (checksumMap.containsKey(convertedName) && checksumMap.get(convertedName)
                            .equals(serviceInfo.getChecksum())) {
                        continue;
                    }
                    // Update the resource:
                    changed = true;
                    if (!serviceInfo.validate()) {
                        resourceMap.remove(convertedName);
                        checksumMap.remove(convertedName);
                        continue;
                    }
                    
                    resourceMap.put(convertedName, convertService(service));
                    checksumMap.put(convertedName, serviceInfo.getChecksum());
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
        if (!Constants.DEFAULT_GROUP.equals(service.getGroup())) {
            return service.getName() + SEPARATOR + service.getGroup() + SEPARATOR + service.getNamespace();
        }
        //DEFAULT_GROUP is invalid for istio,because the istio host only supports: [0-9],[A-Z],[a-z],-,*
        return service.getName() + DEFAULT_SUFFIX + SEPARATOR + service.getNamespace();
    }
    
    private ResourceOuterClass.Resource convertService(Service service) {
        String serviceName = convertName(service);
        ServiceEntryOuterClass.ServiceEntry.Builder serviceEntryBuilder = ServiceEntryOuterClass.ServiceEntry
                .newBuilder().setResolution(ServiceEntryOuterClass.ServiceEntry.Resolution.STATIC)
                .setLocation(ServiceEntryOuterClass.ServiceEntry.Location.MESH_INTERNAL)
                .addHosts(serviceName + SEPARATOR + SERVICE_NAME_SPLITTER);
        
        ServiceInfo serviceInfo = serviceStorage.getData(service);
        
        List<Instance> hosts = serviceInfo.getHosts();
        
        int port = 0;
        for (Instance instance : hosts) {
            if (port == 0) {
                port = instance.getPort();
            }
            if (!instance.isHealthy() || !instance.isEnabled()) {
                continue;
            }
            Map<String, String> metadata = instance.getMetadata();
            if (StringUtils.isNotEmpty(instance.getClusterName())) {
                metadata.put(CLUSTER_PARAM, instance.getClusterName());
            }
            WorkloadEntryOuterClass.WorkloadEntry workloadEntry = WorkloadEntryOuterClass.WorkloadEntry.newBuilder()
                    .setAddress(instance.getIp()).setWeight((int) instance.getWeight()).putAllLabels(metadata)
                    .putPorts(PORT_PARAM, instance.getPort()).build();
            
            serviceEntryBuilder.addEndpoints(workloadEntry);
        }
        
        serviceEntryBuilder.addPorts(
                GatewayOuterClass.Port.newBuilder().setNumber(port).setName(PORT_PARAM).setProtocol(HTTP).build());
        
        ServiceEntryOuterClass.ServiceEntry serviceEntry = serviceEntryBuilder.build();
        
        Optional<ServiceMetadata> serviceMetadata = namingMetadataManager.getServiceMetadata(service);
        ServiceMetadata serviceMetadataGetter = serviceMetadata.orElseGet(ServiceMetadata::new);
        
        Any any = Any.newBuilder().setValue(serviceEntry.toByteString()).setTypeUrl(MESSAGE_TYPE_URL).build();
        MetadataOuterClass.Metadata metadata = MetadataOuterClass.Metadata.newBuilder()
                .setName(SERVICE_NAME_SPLITTER + "/" + serviceName)
                .putAllAnnotations(serviceMetadataGetter.getExtendData()).putAnnotations(VIRTUAL_ANNOTATION, DEFAULT_VIRTUAL)
                .setCreateTime(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build())
                .setVersion(serviceInfo.getChecksum()).build();
        
        return ResourceOuterClass.Resource.newBuilder().setBody(any).setMetadata(metadata).build();
    }
}
