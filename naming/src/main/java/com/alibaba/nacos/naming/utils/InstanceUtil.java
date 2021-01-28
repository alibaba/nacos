/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Instance util.
 *
 * @author xiweng.yy
 */
public class InstanceUtil {
    
    /**
     * Parse {@code InstancePublishInfo} to {@code Instance}.
     *
     * @param service      service of instance
     * @param instanceInfo instance info
     * @return api instance
     */
    public static Instance parseToApiInstance(Service service, InstancePublishInfo instanceInfo) {
        Instance result = new Instance();
        result.setIp(instanceInfo.getIp());
        result.setPort(instanceInfo.getPort());
        result.setServiceName(NamingUtils.getGroupedName(service.getName(), service.getGroup()));
        result.setClusterName(instanceInfo.getCluster());
        Map<String, String> instanceMetadata = new HashMap<>(instanceInfo.getExtendDatum().size());
        for (Map.Entry<String, Object> entry : instanceInfo.getExtendDatum().entrySet()) {
            if (Constants.CUSTOM_INSTANCE_ID.equals(entry.getKey())) {
                result.setInstanceId(entry.getValue().toString());
            } else {
                instanceMetadata.put(entry.getKey(), entry.getValue().toString());
            }
        }
        result.setMetadata(instanceMetadata);
        result.setEphemeral(service.isEphemeral());
        result.setHealthy(instanceInfo.isHealthy());
        return result;
    }
    
    /**
     * Update metadata in {@code Instance} according to {@code InstanceMetadata}.
     *
     * @param instance instance need to be update
     * @param metadata instance metadata
     */
    public static void updateInstanceMetadata(Instance instance, InstanceMetadata metadata) {
        instance.setEnabled(metadata.isEnabled());
        instance.setWeight(metadata.getWeight());
        for (Map.Entry<String, Object> entry : metadata.getExtendData().entrySet()) {
            instance.getMetadata().put(entry.getKey(), entry.getValue().toString());
        }
    }
}
