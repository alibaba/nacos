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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.ServiceEntryWrapper;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.google.protobuf.Timestamp;
import istio.mcp.v1alpha1.MetadataOuterClass.Metadata;
import istio.networking.v1alpha3.GatewayOuterClass;
import istio.networking.v1alpha3.ServiceEntryOuterClass.ServiceEntry;
import istio.networking.v1alpha3.WorkloadEntryOuterClass.WorkloadEntry;
import org.apache.commons.lang.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author special.fy
 */
public class IstioCrdUtil {

    public static final String VALID_DEFAULT_GROUP_NAME = "DEFAULT-GROUP";

    private static final String ISTIO_HOSTNAME = "istio.hostname";

    public static final String VALID_LABEL_KEY_FORMAT = "^([a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?)*/)?((?:[A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])$";
    public static final String VALID_LABEL_VALUE_FORMAT = "^((?:[A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])?$";

    public static String buildServiceNameForServiceEntry(Service service) {
        String group = !Constants.DEFAULT_GROUP.equals(service.getGroup()) ? service.getGroup() : VALID_DEFAULT_GROUP_NAME;

        // DEFAULT_GROUP is invalid for istio,because the istio host only supports: [0-9],[A-Z],[a-z],-,*
        return service.getName() + "." + group + "." + service.getNamespace();
    }

    public static ServiceEntryWrapper buildServiceEntry(String serviceName, String domainSuffix,IstioService istioService) {
        if (istioService.getHosts().isEmpty()) {
            return null;
        }

        ServiceEntry.Builder serviceEntryBuilder = ServiceEntry
                .newBuilder().setResolution(ServiceEntry.Resolution.STATIC)
                .setLocation(ServiceEntry.Location.MESH_INTERNAL);

        int port = 0;
        String protocol = "http";
        String hostname = serviceName;

        for (Instance instance : istioService.getHosts()) {
            if (port == 0) {
                port = instance.getPort();
            }

            if (StringUtils.isNotEmpty(instance.getMetadata().get("protocol"))) {
                protocol = instance.getMetadata().get("protocol");

                if ("triple".equals(protocol) || "tri".equals(protocol)){
                    protocol = "grpc";
                }
            }

            String metaHostname = instance.getMetadata().get(ISTIO_HOSTNAME);
            if (StringUtils.isNotEmpty(metaHostname)) {
                hostname = metaHostname;
            }

            if (!instance.isHealthy() || !instance.isEnabled()) {
                continue;
            }

            Map<String, String> metadata = new HashMap<>(1 << 3);
            if (StringUtils.isNotEmpty(instance.getClusterName())) {
                metadata.put("cluster", instance.getClusterName());
            }

            for (Map.Entry<String,String> entry : instance.getMetadata().entrySet()){
                if (!Pattern.matches(VALID_LABEL_KEY_FORMAT, entry.getKey())){
                    continue;
                }
                if (!Pattern.matches(VALID_LABEL_VALUE_FORMAT, entry.getValue())){
                    continue;
                }
                metadata.put(entry.getKey(), entry.getValue());
            }

            WorkloadEntry workloadEntry = WorkloadEntry.newBuilder()
                    .setAddress(instance.getIp()).setWeight((int) instance.getWeight())
                    .putAllLabels(metadata).putPorts(protocol, instance.getPort()).build();
            serviceEntryBuilder.addEndpoints(workloadEntry);
        }

        serviceEntryBuilder.addHosts(hostname + "." + domainSuffix).addPorts(
                GatewayOuterClass.Port.newBuilder().setNumber(port).setName(protocol).setProtocol(protocol.toUpperCase()).build());
        ServiceEntry serviceEntry = serviceEntryBuilder.build();

        Date createTimestamp = istioService.getCreateTimeStamp();
        Metadata metadata = Metadata.newBuilder()
                .setName(istioService.getNamespace() + "/" + serviceName)
                .putAnnotations("virtual", "1")
                .putLabels("registryType", "nacos")
                .setCreateTime(Timestamp.newBuilder().setSeconds(createTimestamp.getTime() / 1000).build())
                .setVersion(String.valueOf(istioService.getRevision())).build();

        return new ServiceEntryWrapper(metadata, serviceEntry);
    }
}
