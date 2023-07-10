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

package com.alibaba.nacos.naming.utils;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.alibaba.nacos.naming.constants.Constants.DEFAULT_INSTANCE_WEIGHT;
import static com.alibaba.nacos.naming.constants.Constants.PUBLISH_INSTANCE_ENABLE;
import static com.alibaba.nacos.naming.constants.Constants.PUBLISH_INSTANCE_WEIGHT;
import static com.alibaba.nacos.naming.misc.UtilsAndCommons.DEFAULT_CLUSTER_NAME;

/**
 * Utils to generate revision/checksum of distro clients.
 *
 * @author Pixy Yuan
 * on 2021/10/9
 */
public class DistroUtils {
    
    /**
     * Build service key.
     */
    public static String serviceKey(Service service) {
        return service.getNamespace()
                + "##"
                + service.getGroupedServiceName()
                + "##"
                + service.isEphemeral();
    }
    
    /**
     * Calculate hash of unique string built by client's metadata.
     */
    public static int stringHash(Client client) {
        String s = buildUniqueString(client);
        if (s == null) {
            return 0;
        }
        return s.hashCode();
    }
    
    /**
     * Calculate hash for client. Reduce strings in memory and cpu costs.
     */
    public static int hash(Client client) {
        if (!(client instanceof IpPortBasedClient)) {
            return 0;
        }
        return Objects.hash(client.getClientId(),
                client.getAllPublishedService().stream()
                        .map(s -> {
                            InstancePublishInfo ip = client.getInstancePublishInfo(s);
                            double weight = getWeight(ip);
                            Boolean enabled = getEnabled(ip);
                            String cluster = StringUtils.defaultIfBlank(ip.getCluster(), DEFAULT_CLUSTER_NAME);
                            return Objects.hash(
                                    s.getNamespace(),
                                    s.getGroup(),
                                    s.getName(),
                                    s.isEphemeral(),
                                    ip.getIp(),
                                    ip.getPort(),
                                    weight,
                                    ip.isHealthy(),
                                    enabled,
                                    cluster,
                                    ip.getExtendDatum()
                            );
                        })
                        .collect(Collectors.toSet()));
    }
    
    /**
     * Calculate checksum for client.
     */
    public static String checksum(Client client) {
        String s = buildUniqueString(client);
        if (s == null) {
            return "0";
        }
        return MD5Utils.md5Hex(s, Constants.ENCODE);
    }
    
    /**
     * Calculate unique string for client.
     */
    public static String buildUniqueString(Client client) {
        if (!(client instanceof IpPortBasedClient)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(client.getClientId()).append('|');
        client.getAllPublishedService().stream()
                .sorted(Comparator.comparing(DistroUtils::serviceKey))
                .forEach(s -> {
                    InstancePublishInfo ip = client.getInstancePublishInfo(s);
                    double weight = getWeight(ip);
                    Boolean enabled = getEnabled(ip);
                    String cluster = StringUtils.defaultIfBlank(ip.getCluster(), DEFAULT_CLUSTER_NAME);
                    sb.append(serviceKey(s)).append('_')
                            .append(ip.getIp()).append(':').append(ip.getPort()).append('_')
                            .append(weight).append('_')
                            .append(ip.isHealthy()).append('_')
                            .append(enabled).append('_')
                            .append(cluster).append('_')
                            .append(convertMap2String(ip.getExtendDatum()))
                            .append(',');
                });
        return sb.toString();
    }
    
    private static boolean getEnabled(InstancePublishInfo ip) {
        Object enabled0 = ip.getExtendDatum().get(PUBLISH_INSTANCE_ENABLE);
        if (!(enabled0 instanceof Boolean)) {
            return true;
        } else {
            return (Boolean) enabled0;
        }
    }
    
    private static double getWeight(InstancePublishInfo ip) {
        Object weight0 = ip.getExtendDatum().get(PUBLISH_INSTANCE_WEIGHT);
        if (!(weight0 instanceof Number)) {
            return DEFAULT_INSTANCE_WEIGHT;
        } else {
            return ((Number) weight0).doubleValue();
        }
    }
    
    /**
     * Convert Map to KV string with ':'.
     *
     * @param map map need to be converted
     * @return KV string with ':'
     */
    private static String convertMap2String(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return StringUtils.EMPTY;
        }
        StringBuilder sb = new StringBuilder();
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            sb.append(key);
            sb.append(':');
            sb.append(map.get(key));
            sb.append(',');
        }
        return sb.toString();
    }
    
}
