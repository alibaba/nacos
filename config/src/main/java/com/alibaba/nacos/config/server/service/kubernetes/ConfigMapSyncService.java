/*
 *
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.config.server.service.kubernetes;

import com.alibaba.nacos.api.exception.NacosException;
import io.kubernetes.client.openapi.models.V1ConfigMap;

import java.io.IOException;

/**
 * ConfigMap synchronize service.
 *
 * @author wangyixing
 */
public interface ConfigMapSyncService {
    
    /**
     * publish k8s configMap.
     * @param configMap configMap entry
     * @param srcIp client's Ip
     * @throws NacosException exception
     */
    void publishConfigMap(V1ConfigMap configMap, String srcIp) throws NacosException;
    
    /**
     * delete k8s configMap.
     * @param configMap configMap entry
     * @param clientIp client's Ip
     */
    void deleteConfigMap(V1ConfigMap configMap, String clientIp);
    
    /**
     * start watch function.
     * @throws IOException exception
     */
    void startWatchConfigMap() throws IOException;
}
