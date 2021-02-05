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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;

/**
 * Persistent Health operator.
 *
 * @author xiweng.yy
 */
public interface HealthOperator {
    
    /**
     * Manually update healthy status for persistent instance.
     *
     * <p>Only {@code HealthCheckType.NONE} can be manually update status.
     *
     * @param namespace       namespace of service
     * @param fullServiceName full service name like `groupName@@serviceName`
     * @param clusterName     cluster of instance
     * @param ip              ip of instance
     * @param port            port of instance
     * @param healthy         health status of instance
     * @throws NacosException any exception during updating
     */
    void updateHealthStatusForPersistentInstance(String namespace, String fullServiceName, String clusterName,
            String ip, int port, boolean healthy) throws NacosException;
}
