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

package com.alibaba.nacos.istio.util;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.istio.model.IstioEndpoint;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.ServiceEntryWrapper;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.google.protobuf.Timestamp;
import io.envoyproxy.envoy.config.core.v3.TrafficDirection;
import istio.mcp.v1alpha1.MetadataOuterClass;
import istio.networking.v1alpha3.GatewayOuterClass;
import istio.networking.v1alpha3.ServiceEntryOuterClass;
import istio.networking.v1alpha3.WorkloadEntryOuterClass;
import istio.networking.v1alpha3.WorkloadEntryOuterClass.WorkloadEntry;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author special.fy
 */
public class IstioCrdUtil {

    public static final String VALID_DEFAULT_GROUP_NAME = "DEFAULT-GROUP";

    public static final String ISTIO_HOSTNAME = "istio.hostname";

    public static final String VALID_LABEL_KEY_FORMAT = "^([a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?)*/)?((?:[A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])$";
    
    public static final String VALID_LABEL_VALUE_FORMAT = "^((?:[A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])?$";
    
    public static String buildClusterName(TrafficDirection direction, String subset, String hostName, int port) {
        return direction.toString().toLowerCase() + "|" + port + "|" + subset + "|" + hostName;
    }
    
    public static String buildServiceName(Service service) {
        String group = !Constants.DEFAULT_GROUP.equals(service.getGroup()) ? service.getGroup() : VALID_DEFAULT_GROUP_NAME;

        // DEFAULT_GROUP is invalid for istio,because the istio host only supports: [0-9],[A-Z],[a-z],-,*
        return service.getName() + "." + group + "." + service.getNamespace();
    }
    
    public static String parseServiceEntryNameToServiceName(String serviceEntryName, String domain) {
        return serviceEntryName.substring(0, serviceEntryName.length() - domain.length() - 1);
    }
    
    public static String parseClusterNameToServiceName(String clusterName, String domain) {
        String str = clusterName.split("\\|", 4)[3];
        return str.substring(0, str.length() - domain.length() - 1);
    }
    
    public static ServiceEntryWrapper buildServiceEntry(String serviceName, String hostName, IstioService istioService) {
        if (istioService.getHosts().isEmpty()) {
            return null;
        }

        ServiceEntryOuterClass.ServiceEntry.Builder serviceEntryBuilder = ServiceEntryOuterClass.ServiceEntry
                .newBuilder().setResolution(ServiceEntryOuterClass.ServiceEntry.Resolution.STATIC)
                .setLocation(ServiceEntryOuterClass.ServiceEntry.Location.MESH_INTERNAL);

        int port = 0;
        String protocol = "http";
        List<WorkloadEntry> endpoints = buildWorkloadEntry(istioService.getHosts());
        
        serviceEntryBuilder.addHosts(hostName).addPorts(GatewayOuterClass.Port.newBuilder().setNumber(port)
                .setName(protocol).setProtocol(protocol.toUpperCase()).build()).addAllEndpoints(endpoints);
        ServiceEntryOuterClass.ServiceEntry serviceEntry = serviceEntryBuilder.build();
        
        Date createTimestamp = istioService.getCreateTimeStamp();
        MetadataOuterClass.Metadata metadata = MetadataOuterClass.Metadata.newBuilder()
                .setName(istioService.getNamespace() + "/" + serviceName)
                .putAnnotations("virtual", "1")
                .putLabels("registryType", "nacos")
                .setCreateTime(Timestamp.newBuilder().setSeconds(createTimestamp.getTime() / 1000).build())
                .setVersion(String.valueOf(istioService.getRevision())).build();
        
        return new ServiceEntryWrapper(metadata, serviceEntry);
    }
    
    public static List<WorkloadEntryOuterClass.WorkloadEntry> buildWorkloadEntry(List<IstioEndpoint> istioEndpointList) {
        List<WorkloadEntryOuterClass.WorkloadEntry> result = new ArrayList<>();
        
        for (IstioEndpoint istioEndpoint : istioEndpointList) {
            if (!istioEndpoint.isHealthy() || !istioEndpoint.isEnabled()) {
                continue;
            }

            Map<String, String> metadata = new HashMap<>(1 << 3);
            if (StringUtils.isNotEmpty(istioEndpoint.getClusterName())) {
                metadata.put("cluster", istioEndpoint.getClusterName());
            }

            for (Map.Entry<String, String> entry : istioEndpoint.getLabels().entrySet()) {
                if (!Pattern.matches(VALID_LABEL_KEY_FORMAT, entry.getKey())) {
                    continue;
                }
                if (!Pattern.matches(VALID_LABEL_VALUE_FORMAT, entry.getValue())) {
                    continue;
                }
                metadata.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            
            WorkloadEntryOuterClass.WorkloadEntry workloadEntry = WorkloadEntryOuterClass.WorkloadEntry.newBuilder()
                    .setAddress(istioEndpoint.getAdder()).setWeight(istioEndpoint.getWeight())
                    .putAllLabels(metadata).putPorts(istioEndpoint.getProtocol(), istioEndpoint.getPort()).build();
            
            result.add(workloadEntry);
        }
        return result;
    }
}
