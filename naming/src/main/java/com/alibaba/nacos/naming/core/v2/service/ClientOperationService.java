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

package com.alibaba.nacos.naming.core.v2.service;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.constants.Constants;

/**
 * Client operation service.
 *
 * @author xiweng.yy
 */
public interface ClientOperationService {
    
    /**
     * Register instance to service.
     *
     * @param service  service
     * @param instance instance
     * @param clientId id of client
     */
    void registerInstance(Service service, Instance instance, String clientId);
    
    /**
     * Deregister instance from service.
     *
     * @param service  service
     * @param instance instance
     * @param clientId id of client
     */
    void deregisterInstance(Service service, Instance instance, String clientId);
    
    /**
     * Subscribe a service.
     *
     * @param service    service
     * @param subscriber subscribe
     * @param clientId   id of client
     */
    default void subscribeService(Service service, Subscriber subscriber, String clientId) {
    
    }
    
    /**
     * Unsubscribe a service.
     *
     * @param service    service
     * @param subscriber subscribe
     * @param clientId   id of client
     */
    default void unsubscribeService(Service service, Subscriber subscriber, String clientId) {
    
    }
    
    /**
     * get publish info.
     *
     * @param instance {@link Instance}
     * @return {@link InstancePublishInfo}
     */
    default InstancePublishInfo getPublishInfo(Instance instance) {
        InstancePublishInfo result = new InstancePublishInfo(instance.getIp(), instance.getPort());
        if (null != instance.getMetadata() && !instance.getMetadata().isEmpty()) {
            result.getExtendDatum().putAll(instance.getMetadata());
        }
        if (StringUtils.isNotEmpty(instance.getInstanceId())) {
            result.getExtendDatum().put(Constants.CUSTOM_INSTANCE_ID, instance.getInstanceId());
        }
        if (Constants.DEFAULT_INSTANCE_WEIGHT != instance.getWeight()) {
            result.getExtendDatum().put(Constants.PUBLISH_INSTANCE_WEIGHT, instance.getWeight());
        }
        if (!instance.isEnabled()) {
            result.getExtendDatum().put(Constants.PUBLISH_INSTANCE_ENABLE, instance.isEnabled());
        }
        String clusterName = StringUtils.isBlank(instance.getClusterName()) ? UtilsAndCommons.DEFAULT_CLUSTER_NAME
                : instance.getClusterName();
        result.setHealthy(instance.isHealthy());
        result.setCluster(clusterName);
        return result;
    }
}
